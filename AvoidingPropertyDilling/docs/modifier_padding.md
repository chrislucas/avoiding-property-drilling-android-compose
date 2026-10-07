# `Modifier` padding no Jetpack Compose

Guia das opcoes de **padding** que podemos aplicar a um componente de UI
composable atraves do `Modifier`. Padding, no Compose, adiciona espaco **interno**
ao redor do conteudo do componente (empurra o conteudo para dentro), ao contrario
de margin em sistemas de View — no Compose nao existe `margin`; usamos padding
combinado com a ordem dos modifiers para obter o efeito desejado.

Import base:

```kotlin
import androidx.compose.foundation.layout.padding
```

---

## 1. Visao geral das sobrecargas de `padding`

### 1.1 `padding(all)` — mesmo valor nos quatro lados

```kotlin
Text(
    text = "Ola",
    modifier = Modifier.padding(16.dp)
)
```

Aplica o mesmo espacamento em cima, baixo, esquerda e direita.

### 1.2 `padding(horizontal, vertical)` — eixos

```kotlin
Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
```

- `horizontal`: aplicado a **esquerda e direita**.
- `vertical`: aplicado a **cima e baixo**.

Ambos tem valor padrao `0.dp`, entao da para informar so um:

```kotlin
Modifier.padding(horizontal = 16.dp) // vertical = 0.dp
```

E a sobrecarga usada nos campos e botoes deste projeto, por exemplo:

```kotlin
Modifier
    .fillMaxWidth()
    .padding(horizontal = 16.dp, vertical = 8.dp)
```

### 1.3 `padding(start, top, end, bottom)` — lados individuais

```kotlin
Modifier.padding(
    start = 16.dp,
    top = 8.dp,
    end = 16.dp,
    bottom = 24.dp
)
```

- `start` / `end`: lados **relativos a direcao de leitura** (LTR vs RTL).
  Em idiomas LTR (ex.: portugues), `start` = esquerda e `end` = direita;
  em RTL (ex.: arabe) os lados se invertem automaticamente.
- `top` / `bottom`: cima e baixo (nao dependem da direcao).
- Todos os parametros tem padrao `0.dp`, entao informe apenas os que precisar:

```kotlin
Modifier.padding(start = 16.dp, bottom = 8.dp)
```

> Prefira `start`/`end` em vez de "left"/"right". O Compose foi desenhado para
> ser *RTL-aware* por padrao, e nao existe sobrecarga baseada em `left`/`right`
> em `Modifier.padding`.

### 1.4 `padding(PaddingValues)` — objeto reutilizavel

```kotlin
import androidx.compose.foundation.layout.PaddingValues

val contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)

Modifier.padding(contentPadding)
```

Util quando o padding vem de fora do componente (ex.: o `innerPadding` que o
`Scaffold` fornece) ou quando se quer centralizar os valores num unico objeto.

`PaddingValues` tem construtores equivalentes as sobrecargas acima:

```kotlin
PaddingValues(16.dp)                                  // todos os lados
PaddingValues(horizontal = 16.dp, vertical = 8.dp)    // eixos
PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 24.dp) // lados
```

Exemplo real deste projeto, repassando o padding do `Scaffold`:

```kotlin
Scaffold { innerPadding ->
    UserListContent(
        modifier = Modifier.padding(innerPadding), // innerPadding e um PaddingValues
        ...
    )
}
```

---

## 2. A ordem dos modifiers importa

Modifiers sao aplicados **em cadeia, da esquerda para a direita**. A posicao do
`padding` em relacao a outros modifiers (como `background`, `clickable`, `size`,
`border`) muda completamente o resultado.

```kotlin
// padding ANTES do background: o fundo NAO cobre o padding
Modifier
    .padding(16.dp)
    .background(Color.Yellow)

// padding DEPOIS do background: o fundo cobre tudo e o conteudo fica afastado
Modifier
    .background(Color.Yellow)
    .padding(16.dp)
```

Regras praticas:

- `padding` **antes** de `background`/`border` → o espaco fica *fora* da area
  colorida/borda (parece margin).
- `padding` **depois** de `background`/`border` → o espaco fica *dentro* da area
  colorida/borda (empurra o conteudo para dentro).
- `padding` **antes** de `clickable` → a area de clique **inclui** o padding.
- `padding` **depois** de `clickable` → a area de clique **exclui** o padding.

---

## 3. Padding "oficial" do Material e componentes

Alguns componentes expoem seu proprio parametro de padding, que deve ser
preferido ao `Modifier.padding` quando existir, pois respeita a semantica do
componente:

- `LazyColumn` / `LazyRow` / `LazyVerticalGrid` tem `contentPadding: PaddingValues`.
  Diferente de `Modifier.padding`, o `contentPadding` adiciona espaco **sem
  recortar** o scroll — os itens ainda rolam ate as bordas:

  ```kotlin
  LazyColumn(
      contentPadding = PaddingValues(vertical = 8.dp)
  ) { /* items */ }
  ```

- `Scaffold` fornece `innerPadding` (um `PaddingValues`) que deve ser aplicado ao
  conteudo para nao ficar embaixo de top bar / bottom bar / barras do sistema.

- Botoes (`Button`, `TextButton`, etc.) aceitam `contentPadding` para controlar o
  espaco interno entre a borda do botao e o texto/icone.

---

## 4. Padding e janelas/sistema (insets)

Para espacar o conteudo em relacao a barras do sistema, teclado (IME), notch etc.,
use os modifiers de **window insets** em vez de valores fixos de padding:

```kotlin
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.windowInsetsPadding

Modifier
    .systemBarsPadding()   // status bar + navigation bar
    .imePadding()          // teclado virtual
```

Outras opcoes comuns:

| Modifier | Para que serve |
| --- | --- |
| `statusBarsPadding()` | Afasta da barra de status (topo). |
| `navigationBarsPadding()` | Afasta da barra de navegacao (base). |
| `systemBarsPadding()` | Status bar + navigation bar juntas. |
| `imePadding()` | Afasta do teclado virtual quando ele aparece. |
| `safeDrawingPadding()` | Area segura evitando todos os insets de desenho. |
| `displayCutoutPadding()` | Evita o recorte da tela (notch/camera). |
| `windowInsetsPadding(insets)` | Padding a partir de um `WindowInsets` arbitrario. |

Esses modifiers sao reativos: ajustam o padding automaticamente quando o teclado
abre, a orientacao muda, etc. Exemplo real deste projeto:

```kotlin
Scaffold(
    modifier = Modifier
        .fillMaxSize()
        .systemBarsPadding()
        .imePadding()
) { innerPadding -> /* ... */ }
```

---

## 5. Diferenca em relacao a `offset` e `size`

- `padding` reduz o espaco disponivel para o conteudo filho (afeta a medida).
- `offset(x, y)` apenas **desloca** o desenho, sem alterar o espaco ocupado no
  layout — nao substitui padding.
- `size`/`width`/`height` definem dimensoes; combine com padding para controlar o
  espaco interno dentro dessas dimensoes.

---

## 6. Resumo rapido

| Sobrecarga | Assinatura | Uso tipico |
| --- | --- | --- |
| Todos os lados | `padding(all: Dp)` | Espaco uniforme. |
| Eixos | `padding(horizontal: Dp, vertical: Dp)` | Formularios, listas, botoes. |
| Lados | `padding(start, top, end, bottom)` | Ajuste fino, RTL-aware. |
| Objeto | `padding(paddingValues: PaddingValues)` | Reaproveitar/`Scaffold`. |

Boas praticas:

- Use `horizontal`/`vertical` quando os lados opostos tiverem o mesmo valor.
- Use `start`/`end` (nao left/right) para suporte a RTL.
- Prefira `contentPadding` em listas e `innerPadding` do `Scaffold`.
- Para barras de sistema e teclado, use os modifiers de insets, nao `.dp` fixo.
- Lembre: **a ordem dos modifiers altera o resultado**.
