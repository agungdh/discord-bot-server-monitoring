#!/bin/bash
# Mengecek apakah ada parameter yang diberikan untuk pesan
message="${1:-generate commit message di dalam block dan bahasa inggris}"

# Mendapatkan informasi tentang perubahan, penambahan, dan penghapusan file
status_output=$(git status --short)
diff_output=$(git diff)
clear
# Gabungkan hasil status dan diff
if [ -z "$diff_output" ] && [ -z "$status_output" ]; then
  echo -e "$message"
else
  echo -e "$message\n\nStatus Perubahan:\n$status_output\n\nGit Diff:\n$diff_output"
fi