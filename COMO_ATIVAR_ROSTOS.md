# 📸 Como Fazer o Sistema de Rostos Funcionar

## Por que as pessoas aparecem separadas?

O app usa **ML Kit** (Google) para detectar rostos e os agrupa automaticamente.

Existem **dois modos de agrupamento**:

### Modo Básico (sem modelo TFLite)
- Funciona automaticamente — não requer configuração
- Agrupa rostos por características posicionais (tamanho do rosto, proporções, landmarks)
- Pode criar mais grupos do que o necessário para a mesma pessoa

### Modo Avançado (com MobileFaceNet)
- Requer adicionar o arquivo `mobilefacenet.tflite` na pasta `app/src/main/assets/`
- Gera embeddings de 128 dimensões por rosto
- Agrupamento muito mais preciso — mesma pessoa em ângulos e iluminações diferentes

## Como obter o modelo MobileFaceNet

1. Baixe de: https://github.com/sirius-id/mobilefacenet-android
   - Ou busque "mobilefacenet.tflite" no Google

2. Coloque o arquivo em:
   ```
   app/src/main/assets/mobilefacenet.tflite
   ```

3. Recompile o app

## Como funciona o fluxo de análise

1. **App abre** → carrega fotos
2. **Background** → analisa cada foto com ML Kit Face Detection
3. **Para cada rosto encontrado** → extrai embedding (TFLite) ou fingerprint posicional
4. **Compara** com todas as pessoas já conhecidas usando similaridade cosseno
5. **Se similaridade > 0.55** → adiciona à pessoa existente
6. **Se não** → cria nova pessoa
7. **Otimização** (botão ✨ na tela de Pessoas) → mescla automaticamente grupos com similaridade > 0.78

## Dicas para melhores resultados

- Toque em ✨ (otimizar) após a análise terminar para mesclar duplicatas
- Para mesclar manualmente: toque em ✏️ → selecione as pessoas → MESCLAR
- Para renomear: segure o card da pessoa → Renomear
- A análise roda em background — as pessoas aparecem conforme vão sendo detectadas

## Status da análise

A análise aparece como barra de progresso na tela de Pessoas e também em Configurações.
