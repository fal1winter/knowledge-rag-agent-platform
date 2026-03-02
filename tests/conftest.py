"""共享 pytest fixtures。"""

import os
import sys

# 确保导入路径正确
sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "src"))
