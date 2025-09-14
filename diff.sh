#!/bin/bash
# Mengecek apakah ada parameter yang diberikan untuk pesan
message="${1:-generate commit message}"

# Mendapatkan informasi tentang perubahan, penambahan, dan penghapusan file
status_output=$(git status --short)
diff_output=$(git diff)

# Gabungkan hasil status dan diff
if [ -z "$diff_output" ] && [ -z "$status_output" ]; then
  echo -e "$message" | xclip -selection clipboard
else
  echo -e "$message\n\nStatus Perubahan:\n$status_output\n\nGit Diff:\n$diff_output" | xclip -selection clipboard
fi

echo "Git diff, status, and message have been copied to clipboard."
