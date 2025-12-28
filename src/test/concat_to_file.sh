#!/usr/bin/env bash
set -euo pipefail

OUTFILE="all_files.txt"

# очищаем или создаём файл
: > "$OUTFILE"

# собрать только *.java файлы и вывести их содержимое в файл
find . -type f -name "*.java" | sort | while read -r f; do
  cat "$f" >> "$OUTFILE"
  echo >> "$OUTFILE"    # пустая строка-разделитель
done

echo "Готово! Все .java файлы собраны в $OUTFILE"

