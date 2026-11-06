# Referencia de estilo — galería monocroma deportiva

> Análisis anonimizado de la interfaz de un e-commerce deportivo. Se usa como inspiración para la dirección visual de Fútbol Manager (blueprint §7); no se copian nombres, logos ni tipografías de esa marca. Los nombres de tokens y fuentes de este archivo son genéricos.

**Tema:** mixto (blanco y casi negro).

La interfaz se vuelve casi invisible para que el contenido protagonice cada pantalla. Se construye solo con negro, blanco y tres grises, sin color de acento ni gradientes decorativos. La identidad vive en el contenido, no en el chrome. La tipografía es compacta y utilitaria (12–20 px), con un único momento de titular enorme en una fuente de display. Los componentes son mínimos: botones en pastilla (radio 30 px), tarjetas planas, bordes finos y casi nada de elevación. La página alterna superficies blancas con secciones oscuras a sangre, y crea ritmo por contraste y no por ornamento.

## Colores

| Nombre genérico | Valor | Rol |
|---|---|---|
| Tinta | `#111111` | Texto, íconos y contraste fuerte. Relleno del botón principal. |
| Papel | `#ffffff` | Fondo de página, superficie de tarjeta, texto sobre relleno oscuro. |
| Niebla | `#f5f5f5` | Fondos secundarios sutiles, relleno de campos, etiquetas. |
| Hormigón | `#e5e5e5` | Bordes, divisores, separadores de grilla. |
| Acero | `#707072` | Texto secundario, ayudas, íconos chicos, migas de pan. |
| Gris tenue | `#9e9ea0` | Íconos deshabilitados, controles de bajo énfasis. |

## Tipografía

- **Interfaz:** una sans neutra (equivalente a Inter) en pesos 400 y 500; tamaños 12, 14, 16 y 20 px; interlineado 1,5 a 1,75.
- **Títulos medios:** la misma sans en peso 500 a 20–24 px, nunca en negrita.
- **Display:** una sola fuente condensada de display, solo para titulares grandes, con interlineado 1,0.
- **Escala:** caption 12 · body-sm 14 · body 16 · subheading 20 · heading-sm 24 · display hasta 76.
- No se usan pesos mayores a 500.

## Espaciado y formas

- Unidad base de 4 px; escala 4, 8, 12, 16, 20, 24, 36, 48. Densidad compacta.
- Ancho máximo de página 1440 px; separación entre secciones 48 px; padding de tarjeta 12 px; separación de elementos 8 px.
- Radios: botones y etiquetas 30 px; barra de navegación y buscador 24 px; tarjetas e inputs 0.

## Componentes

- **Botón en pastilla (único estilo de botón):** relleno tinta con texto papel, o relleno papel con texto tinta sobre fondos oscuros. Peso 500, 16 px, radio 30 px, sin borde ni sombra; el hover solo cambia la opacidad.
- **Tarjeta plana:** sin borde, sin radio y sin sombra; la separación sale de bordes finos o de espacio en blanco.
- **Etiqueta:** pastilla tinta con texto papel, 12–16 px, radio 30 px.
- **Barra de navegación:** sin relleno ni sombra sobre blanco; una fila utilitaria arriba y otra con categorías; el buscador usa relleno niebla y radio 24 px.
- **Íconos:** de trazo, 1,5–2 px, tinta o acero, 16–20 px, sin variantes rellenas.
- **Pie de página:** columnas de enlaces en acero con separación vertical de 12 px, sin divisores ni fondos.

## Qué sí y qué no

**Sí**
- Usar solo la paleta monocroma; el color vive en el contenido.
- Radio de 30 px en todos los botones.
- Separar capas con bordes finos o espacio, nunca con sombras.
- Pesos 400 y 500.

**No**
- Acentos de color en botones, links, badges o íconos.
- Negritas o seminegritas.
- Sombras en tarjetas, botones o navegación.
- Esquinas redondeadas en tarjetas.
- Más de un tratamiento de CTA por página.
- Gradientes o patrones decorativos detrás del texto.

## Superficies y elevación

| Nivel | Valor | Uso |
|---|---|---|
| 1 | `#ffffff` | Fondo de página y pie. |
| 2 | `#f5f5f5` | Campos, tintes de sección, etiquetas. |
| 3 | `#e5e5e5` | Bordes y separadores. |

No hay sombras: la elevación se comunica con la alternancia claro/oscuro, los bordes finos y el espacio en blanco.

## Variables de referencia

```css
:root {
  --color-ink: #111111;
  --color-paper: #ffffff;
  --color-mist: #f5f5f5;
  --color-concrete: #e5e5e5;
  --color-steel: #707072;
  --color-faint: #9e9ea0;

  --text-caption: 12px;
  --text-body-sm: 14px;
  --text-body: 16px;
  --text-subheading: 20px;
  --text-heading-sm: 24px;

  --font-weight-regular: 400;
  --font-weight-medium: 500;

  --radius-button: 30px;
  --radius-nav: 24px;
  --radius-card: 0;
}
```
