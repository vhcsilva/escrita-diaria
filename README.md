# Escrita Diária

App Android para o ritual de escrita diária à mão, feito para o Galaxy Tab S6 Lite com S Pen.

## O que o app faz

- **Ritual da manhã em 3 blocos**: Onde estou (3 min), Pergunta do dia (8 min) e Direção do dia (4 min). Cada bloco tem cronômetro com pausa e +1 min. Quando o tempo acaba, o app toca um aviso e você escolhe entre ir para o próximo bloco ou continuar escrevendo.
- **Versão rápida (5 min)** para dias corridos.
- **Escrita com a S Pen**: a pressão muda a espessura do traço, o botão lateral apaga e a palma é ignorada.
- **Temas por dia da semana**, com rodízio de 5 semanas. Todas as perguntas podem ser editadas ou coladas de um texto copiado (tela "Editar perguntas").
- **Leitura da letra**: as páginas são convertidas em texto no próprio tablet (ML Kit, português). A ação do Bloco 3 fica fixada na notificação até você marcar como feita.
- **Lembretes**: horário da manhã por dia da semana (com o tema do dia), repetição se você não começar, e de 3 a 6 perguntas ao longo do dia (horários fixos ou aleatórios).
- **Minha semana**: ciclo de 5 semanas, páginas de cada dia, ações para marcar e exportação em PDF.
- **Histórico**: filtros por ano, mês, semana, dia e tema, além de busca no texto de tudo o que foi escrito.

O texto convertido aparece numa fonte cursiva, à escolha entre Dancing Script, Caveat e Great Vibes (licença SIL OFL, em `licenses/`).

Tudo fica salvo só no tablet. O pacote de leitura da letra é baixado uma vez (precisa de internet); depois funciona offline.

## Instalar

1. No tablet, abra a página **Releases** deste repositório e baixe o `.apk` mais recente.
2. Toque no arquivo e permita "instalar apps desconhecidos" para o navegador, quando o Android pedir.
3. Abra o app e permita as notificações.

## Compilar

O GitHub Actions compila a cada push na `main` e publica o APK em Releases. Para compilar localmente, abra a pasta no Android Studio ou rode:

```
./gradlew assembleRelease
```

Kotlin, Jetpack Compose, Room, DataStore e ML Kit Digital Ink Recognition. Android 8.0 ou mais novo.
