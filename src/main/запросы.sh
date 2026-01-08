#!/usr/bin/env bash
set -euo pipefail

# OUTFILE:
# - если пустая строка ("") -> в файл НЕ пишем, только в буфер
# - если не пустая -> пишем в файл + (если есть wl-copy) копируем в буфер
OUTFILE="all_files.txt"
# OUTFILE=""

# === НАСТРОЙКА: перечисляй тут пути (каталоги и/или конкретные файлы) ===
# - Если путь указывает на ФАЙЛ: берём его ВСЕГДА, даже если это не .java
# - Если путь указывает на КАТАЛОГ: рекурсивно берём только *.java внутри
# - Пустые строки игнорируются
TARGETS=(
  #"./src/main/java"
#  "./server"
#  /home/ai/work/SHiNE/SHiNE-server/shine-server-blockchain
   "/home/ai/work/SHiNE/SHiNE-server/shine-server-blockchain"
   "/home/ai/work/SHiNE/SHiNE-server/shine-server-db"
)

RED=$'\033[0;31m'
RESET=$'\033[0m'

warn_red() {
  echo "${RED}WARN:${RESET} $*" >&2
}

# временные файлы
TMP_LIST="$(mktemp)"
TMP_OUT="$(mktemp)"
trap 'rm -f "$TMP_LIST" "$TMP_OUT"' EXIT

# собрать пути
for path in "${TARGETS[@]}"; do
  path="$(printf '%s' "$path" | sed -e 's/^[[:space:]]\+//' -e 's/[[:space:]]\+$//')"
  [[ -z "$path" ]] && continue

  if [[ -f "$path" ]]; then
    printf '%s\n' "$path" >> "$TMP_LIST"
  elif [[ -d "$path" ]]; then
    find "$path" -type f -name "*.java" >> "$TMP_LIST"
  else
    warn_red "Не найдено (пропускаю): $path"
  fi
done

# склеиваем в TMP_OUT
sort -u "$TMP_LIST" | while IFS= read -r f; do
  if [[ ! -f "$f" ]]; then
    warn_red "Файл исчез (пропускаю): $f"
    continue
  fi
  cat "$f" >> "$TMP_OUT"
  echo >> "$TMP_OUT"
done

# если OUTFILE не пуст — пишем файл
if [[ -n "${OUTFILE:-}" ]]; then
  : > "$OUTFILE"
  cat "$TMP_OUT" > "$OUTFILE"
fi

# копирование в буфер (Wayland), если доступно
if command -v wl-copy >/dev/null 2>&1; then
  wl-copy < "$TMP_OUT"
else
  warn_red "wl-copy не найден — в буфер не скопировано."
fi

echo "Готово!"
if [[ -n "${OUTFILE:-}" ]]; then
  echo "Все файлы собраны в $OUTFILE"
else
  echo "OUTFILE пуст — в файл не писали, только буфер (если wl-copy доступен)"
fi
