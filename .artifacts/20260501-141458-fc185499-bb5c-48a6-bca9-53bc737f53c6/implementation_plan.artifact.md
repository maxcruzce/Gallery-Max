# Album Management, Xiaomi Support, OCR and Navigation Improvements

Este plano foca na limpeza e reorganização total da estrutura de álbuns, garantindo conformidade com os requisitos de exibição, criação e navegação.

## User Review Required

- **Navegação**: A tela de Álbuns passará a ser apenas uma lista/grade de capas. Ao clicar em um álbum, uma **nova janela (tela)** será aberta para exibir as fotos/vídeos.
- **Criação de Álbuns**: Novos álbuns físicos serão criados obrigatoriamente no caminho padrão da Xiaomi: `/Pictures/Gallery/owner/`.

## Proposed Changes

### [1. Organização & Visibilidade de Álbuns]

#### [MediaRepository.kt](file:///C:/Users/domma/Downloads/Gallery%20Max/app/src/main/java/com/hypergallery/data/MediaRepository.kt)
- **Ajuste `getAlbums`**:
    - **Principais (Fixados por Padrão)**:
        - `Camera` (DCIM/Camera).
        - `Download` (Raiz de /Download).
        - `WhatsApp` (Super-Álbum virtual).
        - `Gravações` (Super-Álbum virtual: Screenshots + ScreenRecorder).
        - `Galeria` (Super-Álbum: /Pictures/Gallery/owner).
        - `Álbuns Virtuais` criados pelo usuário.
    - **Outros Álbuns**: Todas as outras pastas físicas encontradas (inclusive subpastas de Download).
    - **Lógica de Pinagem**: Se não houver preferência salva, os itens acima recebem `isPinned = true`. Todos podem ser desfixados.
- **Criação de Álbuns**: Ajustar `createAlbum` para usar o caminho `/storage/emulated/0/Pictures/Gallery/owner/` e garantir a criação das pastas se não existirem.

#### [GalleryViewModel.kt](file:///C:/Users/domma/Downloads/Gallery%20Max/app/src/main/java/com/hypergallery/viewmodel/GalleryViewModel.kt)
- **Gestão de Estado**: Separar `mainAlbums` e `otherAlbums` conforme a flag `isPinned`.
- **Navegação**: Ao selecionar um álbum, definir `selectedAlbum` e carregar suas mídias para exibição na nova tela.

---

### [2. UI & Navegação (Nova Janela de Conteúdo)]

#### [AlbumDetailScreen.kt](file:///C:/Users/domma/Downloads/Gallery%20Max/app/src/main/java/com/hypergallery/ui/screens/AlbumDetailScreen.kt)
- Esta tela será a **janela separada** para abrir o conteúdo de qualquer álbum (normal ou virtual).
- Deve suportar visualização em grade, ordenação e seleção de mídias.

#### [AlbumsScreen.kt](file:///C:/Users/domma/Downloads/Gallery%20Max/app/src/main/java/com/hypergallery/ui/screens/AlbumsScreen.kt)
- Atuar apenas como o **seletor/lista** de álbuns.
- Clicar em um álbum dispara o evento que abre a `AlbumDetailScreen`.

---

### [3. Análise Visual & OCR]

#### [VisualAnalysisRepository.kt](file:///C:/Users/domma/Downloads/Gallery%20Max/app/src/main/java/com/hypergallery/data/VisualAnalysisRepository.kt)
- **Correção Rostos Virados**: Aplicar rotação correta antes de extrair o embedding.
- **Persistência OCR**: Garantir que o texto detectado seja salvo e exibido nos detalhes.

#### [MediaDetailsScreen.kt](file:///C:/Users/domma/Downloads/Gallery%20Max/app/src/main/java/com/hypergallery/ui/screens/MediaDetailsScreen.kt)
- Adicionar seção "Análise Visual" com texto OCR e botão de copiar.

---

### [4. Regras HyperOS & Documentos]

#### [MainActivity.kt](file:///C:/Users/domma/Downloads/Gallery%20Max/app/src/main/java/com/hypergallery/MainActivity.kt)
- **Ajuste Documento**: Corrigir Intents para o app `com.miui.extraphoto`.
- **Animações**: Garantir uso de `spring` na transição para a nova janela de álbum.

## Verification Plan

### Manual Verification
1. **Hierarquia**: Verificar se apenas Camera, Download, WhatsApp, Gravações, Galeria e Virtuais aparecem no topo.
2. **Navegação**: Confirmar que ao clicar em um álbum, ele abre em uma tela cheia separada e que o botão "Voltar" retorna à lista de álbuns.
3. **Criação**: Criar um "Álbum de Teste" e verificar via gerenciador de arquivos se a pasta foi criada em `/Pictures/Gallery/owner/`.
4. **OCR**: Abrir detalhes de uma foto e copiar o texto detectado.
