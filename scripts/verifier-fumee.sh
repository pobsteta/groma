#!/usr/bin/env bash
# Vérifie le rapport de l'essai de fumée de l'APK (type de build `fumee`, mission 026, calqué sur Surculus ADR 0011) : au moins un test joué, aucun échec.
# Gradle rend un succès quand l'instrumentation plante avant le premier test (0 test) : ce cas doit faire échouer.
# Usage : scripts/verifier-fumee.sh <dossier des rapports connectedFumeeAndroidTest>
set -euo pipefail
dossier="${1:?dossier des rapports}"
total=0
while IFS= read -r -d '' f; do
  ligne=$(grep -o '<testsuites[^>]*' "$f" | head -1)
  n=$(sed -n 's/.* tests="\([0-9]*\)".*/\1/p' <<<"$ligne")
  ech=$(sed -n 's/.* failures="\([0-9]*\)".*/\1/p' <<<"$ligne")
  err=$(sed -n 's/.* errors="\([0-9]*\)".*/\1/p' <<<"$ligne")
  total=$((total + ${n:-0}))
  if [ "${ech:-0}" != 0 ] || [ "${err:-0}" != 0 ]; then echo "Essai de fumée en échec : $f" >&2; exit 1; fi
done < <(find "$dossier" -name 'TEST-*.xml' -print0)
if [ "$total" -lt 1 ]; then echo "Essai de fumée : 0 test joué (l'instrumentation a planté ?) dans $dossier" >&2; exit 1; fi
echo "Essai de fumée : $total test(s) joué(s), aucun échec."
