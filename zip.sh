#!/bin/bash

# Répertoire courant
WORKSPACE_DIR=$(pwd)
OUTPUT_FILE="$WORKSPACE_DIR/code_output_$(date +%Y%m%d_%H%M%S).txt"

echo "🔍 Recherche des fichiers .fxml et .java dans le workspace..."
echo "📂 Workspace: $WORKSPACE_DIR"
echo "📝 Fichier de sortie: $OUTPUT_FILE"

# Vérifier si le répertoire est accessible
if [ ! -d "$WORKSPACE_DIR" ] || [ ! -w "$WORKSPACE_DIR" ]; then
    echo "❌ Erreur: Le répertoire $WORKSPACE_DIR n'est pas accessible ou non inscriptible."
    exit 1
fi

# Trouver tous les fichiers .ts et .java, en excluant certains dossiers
find "$WORKSPACE_DIR" \
    -type f \( -name "*.fxml" -o -name "*.java" \) \
    ! -path "*/node_modules/*" \
    ! -path "*/target/*" \
    ! -path "*/.git/*" \
    ! -path "*/dist/*" \
    ! -path "*/build/*" > "$WORKSPACE_DIR/file_list.txt" 2>/dev/null

# Vérifier si la liste a été créée
if [ ! -f "$WORKSPACE_DIR/file_list.txt" ]; then
    echo "❌ Erreur: Impossible de créer la liste des fichiers."
    exit 1
fi

# Compter les fichiers
FILE_COUNT=$(wc -l < "$WORKSPACE_DIR/file_list.txt")

# Vérifier si des fichiers ont été trouvés
if [ "$FILE_COUNT" -eq 0 ]; then
    echo "❌ Aucun fichier .ts ou .java trouvé dans le workspace (en excluant node_modules et target)."
    rm "$WORKSPACE_DIR/file_list.txt"
    exit 1
fi

echo "🔢 Nombre de fichiers trouvés: $FILE_COUNT"
echo "📋 Fichiers trouvés:"
cat "$WORKSPACE_DIR/file_list.txt"

# Initialiser le fichier de sortie
echo "" > "$OUTPUT_FILE"
if [ ! -f "$OUTPUT_FILE" ]; then
    echo "❌ Erreur: Impossible de créer $OUTPUT_FILE. Vérifiez les permissions."
    rm "$WORKSPACE_DIR/file_list.txt"
    exit 1
fi

# Pour chaque fichier, ajouter son chemin et son contenu
while IFS= read -r file; do
    echo "📄 Ajout de: $file"
    echo -e "\n\n// ============================================" >> "$OUTPUT_FILE"
    echo -e "// FICHIER: $file" >> "$OUTPUT_FILE"
    echo -e "// ============================================\n" >> "$OUTPUT_FILE"
    if ! cat "$file" >> "$OUTPUT_FILE" 2>/dev/null; then
        echo "⚠️ Impossible de lire $file, fichier ignoré."
    fi
done < "$WORKSPACE_DIR/file_list.txt"

echo "✅ Fichier créé avec succès: $OUTPUT_FILE"
echo "📋 $FILE_COUNT fichiers copiés (code source uniquement, pas d'images)"

# Nettoyer la liste temporaire
rm "$WORKSPACE_DIR/file_list.txt"