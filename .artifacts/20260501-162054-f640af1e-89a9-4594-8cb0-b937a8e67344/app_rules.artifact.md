# Regras de Implementação e Funções Obrigatórias

Este documento contém as regras de negócio e funcionalidades que devem ser preservadas em todas as futuras modificações do código. **Não remova ou altere estas funções sem solicitação expressa.**

## 1. Organização de Álbuns Virtuais (Janela de Álbuns)

### Ordem de Exibição Fixa
A lista principal de álbuns deve sempre seguir esta prioridade:
1.  **Câmera**: Pasta física DCIM/Camera.
2.  **Gravações**: Super-álbum virtual agrupando Screenshots e ScreenRecorder.
3.  **Download**: Apenas os arquivos na raiz da pasta `/Download`. Subpastas dentro de Download devem ser mostradas individualmente em "Mais Álbuns".
4.  **WhatsApp**: Super-álbum virtual agrupando:
    *   Images e Video (raíz do Media do WhatsApp).
    *   Pastas `Sent` e `Private` (Ocultas).
    *   Mapeamento de contatos (se o backup .txt estiver ativo).
5.  **Galeria**: Super-álbum virtual agrupando todas as pastas dentro de `/Pictures/Gallery/owner` (Padrão Xiaomi).

### Lógica de Agrupamento
*   **Super-Álbuns**: São entradas únicas na tela inicial que, ao serem clicadas, mostram as subpastas físicas que os compõem.
*   **Identificação Xiaomi**: Priorizar a detecção de caminhos que contenham `/owner/` para o álbum virtual "Galeria".

## 2. Performance e Carregamento (Janela Fotos)

### Carregamento Progressivo (Progressive Loading)
*   O app deve carregar primeiro os **100 itens mais recentes** de cada filtro (Todas, Câmera, Favoritos, Vídeos).
*   O restante deve ser carregado em background para não travar a interface.

### Cache de Memória (Instant Switch)
*   Os primeiros 100 itens de cada aba devem ser mantidos em um `Map` na `MediaRepository` para que a troca de abas entre "Câmera" e "Todas" seja instantânea após o primeiro carregamento.

### Busca Paralela
*   Imagens e Vídeos devem ser buscados usando `async/await` concorrente para reduzir o tempo de resposta do banco de dados MediaStore.

## 3. Navegação Universal (Go to Album)

### Busca Recursiva Global
*   Ao clicar em um nome de álbum nos detalhes de uma foto, o sistema deve usar a função `getAllAlbumsFlat()` para escanear toda a hierarquia (física e virtual).
*   Deve ser capaz de "perfurar" as camadas virtuais (ex: entrar em WhatsApp -> Fotos Enviadas) para levar o usuário ao local exato.

## 4. UI e Experiência do Usuário (UX)

### Transições HyperOS
*   **Spring Animations**: Usar animações de mola (`spring`) para troca de abas e abertura de janelas.
*   **Scale Transitions**: Janelas de detalhes e visualizador devem abrir com efeito de zoom-in (escala).

### Auto-Hide Inteligente
*   **FastScroller**: Fica invisível por padrão. Aparece apenas ao interagir e some após 2 segundos de inatividade para não interferir nos cliques na lateral direita.
*   **Video Controls**: Controles de vídeo somem após 3 segundos de reprodução ativa.

## 5. Integração Xiaomi/MIUI

### Ajuste de Documento
*   A função "Ajustar Documento" deve mirar exclusivamente os Intents do app `com.miui.extraphoto` (Bokeh) e `com.miui.mediaeditor`, usando as flags `is_document_mode=true`.

---
*Este arquivo deve ser consultado antes de qualquer refatoração de navegação ou carregamento.*
