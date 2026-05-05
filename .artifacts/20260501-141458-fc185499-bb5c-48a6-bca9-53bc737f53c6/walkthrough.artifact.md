# Walkthrough - Reorganização de Álbuns e Melhorias de Análise

Este documento resume as mudanças implementadas para limpar o código e reorganizar a experiência do usuário, focando na hierarquia de álbuns, suporte Xiaomi e inteligência visual.

## 1. Nova Estrutura de Álbuns

A tela de álbuns foi simplificada para mostrar apenas o essencial no topo, seguindo a regra de "Álbuns Principais" e "Outros Álbuns".

- **Álbuns Principais (Fixados por Padrão)**:
    - `Câmera`: Fotos físicas do dispositivo.
    - `Download`: Apenas arquivos na raiz da pasta `/Download`.
    - `WhatsApp`: Super-álbum virtual agrupando fotos e vídeos.
    - `Gravações`: Super-álbum virtual para Screenshots e ScreenRecorder.
    - `Galeria`: Super-álbum para a pasta padrão Xiaomi (`/Pictures/Gallery/owner`).
    - `Álbuns Virtuais`: Todos os criados pelo usuário.
- **Outros Álbuns**: Qualquer outra pasta com mídia encontrada no dispositivo.
- **Gestão de Pinagem**: O usuário tem controle total para "Desfixar" álbuns de sistema ou "Fixar" pastas de terceiros.

## 2. Navegação em Janelas Separadas

Para manter a organização, a visualização do conteúdo de um álbum agora ocorre em uma janela dedicada.
- Ao clicar em uma capa de álbum na lista, o app navega para a `AlbumDetailScreen`.
- Isso separa a gestão da lista (seletor) da visualização das mídias (conteúdo).

## 3. Integração Xiaomi e Criação de Pastas

- **Caminho Padrão**: Novos álbuns normais são criados em `/storage/emulated/0/Pictures/Gallery/owner/`.
- **Ajuste de Documento**: O botão de ajustar documento agora mira especificamente o `com.miui.mediaeditor` e o `com.miui.extraphoto` com flags de modo documento.

## 4. Análise Visual (OCR e Rostos)

- **Texto nas Imagens**:
    - O texto detectado pelo ML Kit é exibido nos Detalhes da mídia.
    - Adicionado botão de "Copiar Texto" nos detalhes.
    - O texto é indexado para busca na aba principal de fotos.
- **Detecção de Rostos**:
    - Corrigido o loop de 4 orientações (0°, 90°, 180°, 270°) para garantir que rostos em fotos verticais ou de ponta-cabeça sejam detectados.
    - O embedding é extraído do bitmap rotacionado corretamente.
    - `SIMILARITY_THRESHOLD` reduzido para `0.58f` para melhorar o agrupamento automático.

## Verificação Técnica

- **Persistência**: As preferências de pinagem e o cache de OCR são salvos via `SharedPreferences`.
- **Performance**: Mantido o carregamento progressivo e o cache de memória para trocas instantâneas entre abas.
- **Transições**: Uso de `spring` animations para uma experiência fluida no estilo HyperOS.
