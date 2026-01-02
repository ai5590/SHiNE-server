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

# скопировать весь файл в буфер обмена (Wayland)
wl-copy < "$OUTFILE"

echo "Готово!"
echo "Все .java файлы собраны в $OUTFILE"
echo "Содержимое скопировано в буфер обмена (Wayland)"
