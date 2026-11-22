# Auditoria dos IDs de itens e skins V3

A varredura analisou os 157 XMLs de itens ativos do arquivo items.rar e
comparou seus 10.495 registros com o catálogo V3 atualizado.

- Os 187 IDs de ativação têm exatamente uma definição. O XML instalado,
  MileniumSkinItems.xml, é idêntico ao publicado no pacote V3 corrigido.
- Os 303 IDs internos do catálogo são únicos. Nenhum ID de ativação é
  usado também como ID visual.
- Dos 549 IDs visuais, 222 também têm uma definição de item na pack.
  Todos têm tipo e slot compatíveis com a referência da skin.
- O ID preexistente 10014 é a única duplicata entre as definições de item.

| ID | XML | Nome |
| --- | --- | --- |
| 10014 | items/armors/Titanium.xml | Titanium Robe{PvP} Armor |
| 10014 | items/armors/Vesper Crystal.xml | Vesper Crystal Helmet |

O ID 10014 não é referenciado pelas skins V3 como item de ativação ou
visual. A auditoria não alterou os XMLs nem os IDs do datapack.
