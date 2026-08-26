# Changelog

## 2.0.0

Renamed project (`indenting-xml-stream-writer` → `pretty-xml-stream-writer`), Maven artifactId
(`dev.wilhelms:indenting-xml-stream-writer` → `dev.wilhelms:pretty-xml-stream-writer`), Java module
(`dev.wilhelms.stax.indenting` → `dev.wilhelms.stax.pretty`), and main class
(`IndentingXMLStreamWriter` → `PrettyXMLStreamWriter`) to reflect its now-broader scope. All are
breaking changes; update your dependency coordinates and class references together.

- Added optional, element-scoped attribute ordering via the new `AttributeOrder` functional
  interface, passed as an optional 4th constructor parameter. `null` (the default via the existing
  1- and 3-arg constructors) keeps attributes in write order, unchanged from 1.x.
  `AttributeOrder.comparatorFor(String elementLocalName)` is consulted once per element and returns
  a `Comparator<String>` over that element's attribute local names, or `null` to leave that
  element's attributes alone - scoped per element rather than global, since the same attribute name
  can mean different things (or need different treatment) on different elements.
- Namespace declarations (`writeNamespace`/`writeDefaultNamespace`) are never reordered, regardless
  of `AttributeOrder`: they are always written first, in declaration order.
- `AttributeOrder.comparatorFor` takes and compares `QName`, not `String`: a bare local name cannot
  distinguish two elements or attributes that share a local name but differ in namespace (e.g. a
  plain `type` attribute versus `xsi:type`), so both the element and its attributes are identified
  by their qualified name.
- Considered and deliberately rejected: wrapping long attribute lists across multiple lines. Every
  StAX implementation tested (the JDK reference implementation, and Woodstox including its
  `XMLStreamWriter2.writeRaw()` extension) commits a start tag - and its closing `>` - the instant
  anything is written after the first attribute, after which further attributes throw
  `XMLStreamException`. This is not implementation-specific behavior to work around, so this library
  does not attempt attribute line-wrapping; see the README for details.

## 1.0.2

- Close buffered open elements when writing or closing a document.
- Fixed `writeAttribute()`/`writeNamespace()`/`writeDefaultNamespace()` silently discarding their call instead of throwing when invoked after a top-level `writeEmptyElement()` had already been flushed to the delegate (e.g. via `flush()`, `close()`, or `writeEndDocument()`).

## 1.0.1

- Fixed `writeEmptyElement()` at the top level not closing when followed only by `flush()` (no further write call), which could leave the tag unclosed on delegates that defer closing an empty element's start tag. The fix keeps using the delegate's `writeEmptyElement()`, so its self-closing guarantee is preserved even on delegates not configured to auto-collapse adjacent start/end tags.

## 1.0.0

Initial public release.

- `IndentingXMLStreamWriter`, a dependency-free `XMLStreamWriter` decorator that indents element-only XML content without touching text, CDATA, or entity content.
- Mixed-content detection retains events for an open element until it closes, so text appearing after a child element (`<p><b/>tail</p>`) is still detected correctly, including in nested element-only children of a mixed-content ancestor.
- Configurable `indent` and `newLine` strings (constructor parameters); empty strings are allowed.
- No artificial whitespace is added between `writeStartElement()` and an immediately following `writeEndElement()`, so writers like Woodstox can still self-close empty elements.
- Verified against the JDK reference StAX implementation, Woodstox, and Jakarta JAXB 4.x marshalling.
