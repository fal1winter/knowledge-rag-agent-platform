#!/usr/bin/env python3
"""QLoRA fine-tuning entrypoint for lightweight intent and rewrite models."""

from __future__ import annotations

import argparse
from pathlib import Path


def parse_args():
    parser = argparse.ArgumentParser()
    parser.add_argument('--base-model', required=True)
    parser.add_argument('--dataset', required=True)
    parser.add_argument('--output-dir', required=True)
    parser.add_argument('--task', choices=['intent', 'rewrite'], required=True)
    parser.add_argument('--epochs', type=float, default=2.0)
    parser.add_argument('--learning-rate', type=float, default=2e-4)
    parser.add_argument('--batch-size', type=int, default=4)
    parser.add_argument('--gradient-accumulation-steps', type=int, default=8)
    parser.add_argument('--max-seq-length', type=int, default=2048)
    return parser.parse_args()


def main():
    args = parse_args()
    from datasets import load_dataset
    from peft import LoraConfig, prepare_model_for_kbit_training
    from transformers import AutoModelForCausalLM, AutoTokenizer, BitsAndBytesConfig, TrainingArguments
    from trl import SFTTrainer

    dataset = load_dataset('json', data_files=args.dataset, split='train')
    tokenizer = AutoTokenizer.from_pretrained(args.base_model, trust_remote_code=True)
    if tokenizer.pad_token is None:
        tokenizer.pad_token = tokenizer.eos_token

    quant_config = BitsAndBytesConfig(
        load_in_4bit=True,
        bnb_4bit_quant_type='nf4',
        bnb_4bit_use_double_quant=True,
        bnb_4bit_compute_dtype='bfloat16',
    )
    model = AutoModelForCausalLM.from_pretrained(
        args.base_model,
        quantization_config=quant_config,
        device_map='auto',
        trust_remote_code=True,
    )
    model = prepare_model_for_kbit_training(model)
    lora_config = LoraConfig(
        r=16,
        lora_alpha=32,
        target_modules=['q_proj', 'k_proj', 'v_proj', 'o_proj', 'gate_proj', 'up_proj', 'down_proj'],
        lora_dropout=0.05,
        bias='none',
        task_type='CAUSAL_LM',
    )

    def format_row(row):
        if args.task == 'intent':
            return (
                '<|im_start|>system\nClassify RAG route intent. Return JSON.<|im_end|>\n'
                f"<|im_start|>user\n{row['query']}<|im_end|>\n"
                f"<|im_start|>assistant\n{row['label_json']}<|im_end|>"
            )
        return (
            '<|im_start|>system\nRewrite the query for retrieval. Return JSON.<|im_end|>\n'
            f"<|im_start|>user\ncontext={row.get('context', [])}\nquery={row['query']}<|im_end|>\n"
            f"<|im_start|>assistant\n{row['target_json']}<|im_end|>"
        )

    training_args = TrainingArguments(
        output_dir=args.output_dir,
        num_train_epochs=args.epochs,
        learning_rate=args.learning_rate,
        per_device_train_batch_size=args.batch_size,
        gradient_accumulation_steps=args.gradient_accumulation_steps,
        logging_steps=20,
        save_steps=200,
        bf16=True,
        report_to=[],
    )
    trainer = SFTTrainer(
        model=model,
        tokenizer=tokenizer,
        train_dataset=dataset,
        peft_config=lora_config,
        formatting_func=format_row,
        max_seq_length=args.max_seq_length,
        args=training_args,
    )
    trainer.train()
    Path(args.output_dir).mkdir(parents=True, exist_ok=True)
    trainer.model.save_pretrained(args.output_dir)
    tokenizer.save_pretrained(args.output_dir)


if __name__ == '__main__':
    main()
