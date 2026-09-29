#!/bin/bash

# Setup script for Git hooks
echo "Setting up Pre-commit for LAA Info and Advice Datastore..."

# Install prek globally
echo "\nInstalling prek globally"
curl --proto '=https' --tlsv1.2 \
-LsSf https://raw.githubusercontent.com/ministryofjustice/devsecops-hooks/a3f792a077eb216c2e9ac9a4c2eac34cea618ee2/prek/prek-installer.sh | sh

# Activate prek in the repository
echo "\nInstalling prek within the repository"
export PATH="$HOME/.local/bin:$PATH"
prek install

HOOK_PATH="$(git rev-parse --git-path hooks)/pre-commit"
if [[ "$(uname -s)" == "Darwin" && -f "$HOOK_PATH" ]] && ! grep -q 'Homebrew-installed tools' "$HOOK_PATH"; then
	HOOK_TMP="$(mktemp "${HOOK_PATH}.XXXXXX")"
	trap 'rm -f "$HOOK_TMP"' EXIT
	awk '
		/^# Check if the full path to prek is executable/ {
			print "# Make Homebrew-installed tools available when Git runs from a GUI client."
			print "for BREW_BIN in /opt/homebrew/bin /usr/local/bin; do"
			print "    if [ -x \"$BREW_BIN/gitleaks\" ]; then"
			print "        PATH=\"$BREW_BIN:$PATH\""
			print "        export PATH"
			print "        break"
			print "    fi"
			print "done"
			print ""
		}
		{ print }
	' "$HOOK_PATH" > "$HOOK_TMP"
	chmod u+x "$HOOK_TMP"
	mv "$HOOK_TMP" "$HOOK_PATH"
	trap - EXIT
fi

echo "Git hooks setup complete!"
echo "The pre-commit hook will now:"
echo "  1. Run Spotless formatting on staged Java files"
echo "  2. Run Checkstyle validation on formatted files"
echo "  3. Run Ministry of Justice - Secrets Scanner"
echo "To manually run Spotless formatting: ./gradlew spotlessApply"
echo "To check Spotless compliance: ./gradlew spotlessCheck"