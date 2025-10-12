"""Neo4j 知识图谱多跳检索适配器。"""

from dataclasses import dataclass
from typing import Dict, Iterable, List, Tuple

from rag_agent_platform.models import RetrievalHit


@dataclass
class GraphPath:
    nodes: List[str]
    relationships: List[str]
    score: float
    evidence: str


class Neo4jGraphRetriever:
    def __init__(self, uri: str, username: str = "neo4j", password: str = "password", fallback: "LocalGraphRetriever | None" = None):
        self.uri = uri
        self.username = username
        self.password = password
        self.fallback = fallback
        self._driver = None

    def _neo4j(self):
        if self._driver is None:
            from neo4j import GraphDatabase
            self._driver = GraphDatabase.driver(self.uri, auth=(self.username, self.password))
        return self._driver

    def multi_hop_search(self, tenant_id: str, entities: List[str], hops: int = 3) -> List[GraphPath]:
        if not entities:
            return []
        cypher = """
        MATCH p=(a)-[*1..$hops]-(b)
        WHERE a.tenant_id = $tenant_id AND a.name IN $entities
        WITH p, length(p) AS path_len
        RETURN [n IN nodes(p) | coalesce(n.name, n.id)] AS nodes,
               [r IN relationships(p) | type(r)] AS relationships,
               path_len
        ORDER BY path_len ASC
        LIMIT 20
        """
        try:
            with self._neo4j().session() as session:
                rows = session.run(cypher, tenant_id=tenant_id, entities=entities, hops=hops)
                paths = []
                for row in rows:
                    nodes = [str(node) for node in row['nodes']]
                    relationships = [str(rel) for rel in row['relationships']]
                    evidence = self._format_evidence(nodes, relationships)
                    paths.append(GraphPath(nodes=nodes, relationships=relationships, score=1.0 / max(1, int(row['path_len'])), evidence=evidence))
                return paths
        except Exception:
            if self.fallback is not None:
                return self.fallback.multi_hop_search(tenant_id, entities, hops)
            raise

    def to_hits(self, tenant_id: str, paths: List[GraphPath]) -> List[RetrievalHit]:
        return [
            RetrievalHit(
                chunk_id=f"graph-path:{index}",
                document_id="neo4j",
                text=path.evidence,
                score=path.score,
                source="neo4j_multi_hop",
                tenant_id=tenant_id,
                citation={"nodes": path.nodes, "relationships": path.relationships},
                metadata={"path": path.__dict__},
            )
            for index, path in enumerate(paths)
        ]

    def upsert_entities(self, tenant_id: str, entities: List[Dict]) -> None:
        cypher = """
        UNWIND $entities AS entity
        MERGE (n:Entity {tenant_id: $tenant_id, name: entity.name})
        SET n += coalesce(entity.properties, {})
        WITH n, entity
        UNWIND coalesce(entity.relations, []) AS rel
        MERGE (m:Entity {tenant_id: $tenant_id, name: rel.target})
        MERGE (n)-[r:RELATED {type: rel.type}]->(m)
        SET r += coalesce(rel.properties, {})
        """
        try:
            with self._neo4j().session() as session:
                session.run(cypher, tenant_id=tenant_id, entities=entities)
        except Exception:
            if self.fallback is not None:
                self.fallback.upsert_entities(tenant_id, entities)
            else:
                raise

    def _format_evidence(self, nodes: List[str], relationships: List[str]) -> str:
        if not nodes:
            return ""
        parts = [nodes[0]]
        for rel, node in zip(relationships, nodes[1:]):
            parts.append(f"-[{rel}]-{node}")
        return "".join(parts)


class LocalGraphRetriever:
    def __init__(self):
        self.edges: Dict[str, List[Tuple[str, str, str]]] = {}

    def upsert_entities(self, tenant_id: str, entities: Iterable[Dict]) -> None:
        for entity in entities:
            name = str(entity.get('name', ''))
            for rel in entity.get('relations', []) or []:
                target = str(rel.get('target', ''))
                rel_type = str(rel.get('type', 'RELATED'))
                self.edges.setdefault(tenant_id, []).append((name, rel_type, target))

    def multi_hop_search(self, tenant_id: str, entities: List[str], hops: int = 3) -> List[GraphPath]:
        graph = self.edges.get(tenant_id, [])
        results: List[GraphPath] = []
        frontier = [(entity, [entity], []) for entity in entities]
        for _ in range(hops):
            next_frontier = []
            for current, nodes, rels in frontier:
                for source, rel_type, target in graph:
                    if source == current and target not in nodes:
                        new_nodes = nodes + [target]
                        new_rels = rels + [rel_type]
                        evidence = self._format_evidence(new_nodes, new_rels)
                        results.append(GraphPath(new_nodes, new_rels, 1.0 / len(new_nodes), evidence))
                        next_frontier.append((target, new_nodes, new_rels))
            frontier = next_frontier
        return results[:20]

    def _format_evidence(self, nodes: List[str], relationships: List[str]) -> str:
        parts = [nodes[0]]
        for rel, node in zip(relationships, nodes[1:]):
            parts.append(f"-[{rel}]-{node}")
        return "".join(parts)
