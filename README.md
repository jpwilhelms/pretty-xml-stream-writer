# Pretty XMLStreamWriter

[![Verify](https://github.com/jpwilhelms/pretty-xml-stream-writer/actions/workflows/verify.yml/badge.svg)](https://github.com/jpwilhelms/pretty-xml-stream-writer/actions/workflows/verify.yml)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](https://www.apache.org/licenses/LICENSE-2.0)

Small Java 17 library that decorates an existing `XMLStreamWriter` to indent element-only XML and, optionally, order each element's attributes. It is a modern, dependency-free alternative to older `stax-utils` implementations and `com.sun.xml.txw2.output.IndentingXMLStreamWriter`; it uses only the JDK StAX API.

Project page: [github.com/jpwilhelms/pretty-xml-stream-writer](https://github.com/jpwilhelms/pretty-xml-stream-writer). The API package is `dev.wilhelms.stax`; the Java module is `dev.wilhelms.stax.pretty`.

> This library was written with the help of an AI coding assistant (Claude). The design, review, and all changes were guided and checked by a human maintainer, but you should factor this into your own review before relying on it.

## Maven

```xml
<dependency>
  <groupId>dev.wilhelms</groupId>
  <artifactId>pretty-xml-stream-writer</artifactId>
  <version>2.0.0</version>
</dependency>
```

> Renamed from `indenting-xml-stream-writer` (formerly `IndentingXMLStreamWriter`) in 2.0.0. See [CHANGELOG.md](CHANGELOG.md) for the migration.

## Usage

```java
XMLStreamWriter raw = XMLOutputFactory.newFactory().createXMLStreamWriter(output);
XMLStreamWriter writer = new PrettyXMLStreamWriter(raw, "  ", "\n");
```

`indent` and `newLine` are immutable constructor parameters. The exact configured string, such as `"\n"` or `"\r\n"`, is passed to the delegate; the library deliberately does not use `System.lineSeparator()`. An `XMLStreamWriter` may still escape carriage returns while serializing character data, so the final bytes remain the delegate's responsibility. Empty `indent` and `newLine` values are allowed.

## Attribute ordering

Attributes are, by default, written in the order they were passed to `writeAttribute()` (matching plain `XMLStreamWriter` behavior). To reorder them, supply an `AttributeOrder`:

```java
AttributeOrder attributeOrder = element ->
    "field".equals(element.getLocalPart()) ? Comparator.comparing(attr -> !"name".equals(attr.getLocalPart())) : null;

XMLStreamWriter writer = new PrettyXMLStreamWriter(raw, "  ", "\n", attributeOrder);
```

`AttributeOrder.comparatorFor(QName element)` is consulted once per element, right before its attributes are written, and returns a `Comparator<QName>` over attribute qualified names - or `null` to leave that element's attributes in their original order. Both the element and its attributes are identified by `QName` (namespace URI + local name), not local name alone: a plain `String` cannot distinguish, say, a bare `type` attribute from `xsi:type`. Scoping by element means the same attribute can be ordered differently, or not at all, depending on which element it appears on; a single global comparator is rarely what you want once a writer is reused across more than one element shape.

Namespace declarations (`writeNamespace`/`writeDefaultNamespace`) are never reordered by an `AttributeOrder` - they are always written first, in the order they were declared, since they are a distinct concept from an element's own attributes.

**No attribute line-wrapping.** This library does not wrap long attribute lists across multiple lines, and does not intend to add it. Doing so would require writing a newline between two `writeAttribute()` calls on the same still-open start tag. Every StAX implementation tested - the JDK reference implementation, and Woodstox including its `XMLStreamWriter2.writeRaw()` extension - commits the tag (and its closing `>`) the instant anything else is written, after which further attributes throw `XMLStreamException`. That isn't implementation-specific behavior to work around; it's how the open-tag/attribute state machine works everywhere this was tested, so a portable `XMLStreamWriter` decorator cannot do it without writing raw bytes directly to the underlying stream - which would break working with an arbitrary delegate.

## JAXB and Woodstox

The main library has no JAXB dependency. The usual StAX integration works with Jakarta JAXB 4.x:

```java
Marshaller marshaller = context.createMarshaller();
XMLStreamWriter woodstox = outputFactory.createXMLStreamWriter(outputStream, "UTF-8");
PrettyXMLStreamWriter writer = new PrettyXMLStreamWriter(woodstox, "  ", "\r\n");
marshaller.marshal(value, writer);
writer.flush();
```

Woodstox automatic empty-element output can be enabled with:

```java
outputFactory.setProperty(XMLOutputFactory2.P_AUTOMATIC_EMPTY_ELEMENTS, true);
```

The decorator never writes artificial whitespace between `writeStartElement()` and an immediately following `writeEndElement()`. Woodstox can therefore still serialize this as `<foo/>` rather than `<foo></foo>`.

## Mixed content and streaming

For each open element, StAX events are retained until the element closes. Only then can the writer reliably determine whether it contains text, CDATA, or entity content—even for `<p><b/>tail</p>`. If it does, the library adds no pretty-print whitespace inside that element. Element-only structures, comments, and processing instructions are indented.

Consequently, bytes for an open element are intentionally sent to the delegated writer only at `writeEndElement()`. Completed top-level events are emitted after the root element closes and on `flush()`. This is normal for JAXB because `marshal` closes the root element before callers typically invoke `flush()`.

Instances are not thread-safe. `flush()` and `close()` are forwarded to the decorated writer. Per the `XMLStreamWriter.close()` contract, this must not close the underlying output stream; whether the destination itself is closed is entirely up to the delegate implementation.
