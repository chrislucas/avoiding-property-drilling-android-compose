# 2. Caminho onde você clonou o repositório do Google
LOCAL_REPO="$HOME/Documents/android-skills-repo"

echo "$LOCAL_REPO"

if [ ! -d "$LOCAL_REPO" ]; then
    echo "❌ Erro: Repositório central não encontrado em $LOCAL_REPO"
    return 1
fi

echo "🔗 Mapeando Android Skills oficiais do Google no Kiro..."

# 3. Varre o repositório buscando as regras e criando links com nomes limpos
find "$LOCAL_REPO" -name "SKILL.md" | while read -r skill_path; do
    # Extrai o nome da categoria/subpasta para não colidir os nomes dos arquivos
    # Ex: transforma 'system/edge-to-edge/SKILL.md' em 'edge-to-edge.md'
    folder_name=$(basename "$(dirname "$skill_path")")

    # Cria o link simbólico na pasta do Kiro
    ln -sf "$skill_path" ".kiro/steering/${folder_name}.md"
done

echo "✅ Concluído! O Kiro agora está calibrado com as Android Skills do Google."