package dev.wilhelms.stax;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import javax.xml.namespace.NamespaceContext;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

/**
 * An {@link XMLStreamWriter} decorator that indents element-only content and, optionally, orders
 * each element's attributes - without changing text, CDATA, or entity content.
 *
 * <p>Events for an open element are retained until that element closes. This is
 * necessary to detect mixed content even when its text appears after a child
 * element. It also ensures no whitespace is put inside an empty element, and lets attributes be
 * reordered before they are ever written to the delegate.</p>
 *
 * <p>Attribute ordering is optional (see {@link AttributeOrder}) and, when configured, is scoped
 * per element (identified by {@link QName}, not local name alone - see {@link AttributeOrder}).
 * Namespace declarations ({@code writeNamespace}/{@code writeDefaultNamespace})
 * are never reordered - they are always written first, in the order they were declared - since they
 * are a distinct concept from an element's own attributes.</p>
 *
 * <p>There is deliberately no support for wrapping long attribute lists across multiple lines: doing
 * so would require writing a newline between two {@code writeAttribute()} calls on the same open
 * start tag, which every StAX implementation tested (the JDK reference implementation and Woodstox,
 * including its {@code XMLStreamWriter2.writeRaw()} extension) rejects by committing the tag - and
 * thus its closing {@code >} - the moment anything else is written, after which further attributes
 * throw. That is not implementation-specific behavior to work around; it is how the open-tag/attribute
 * state machine works everywhere this was tested, so this decorator does not attempt it.</p>
 */
public final class PrettyXMLStreamWriter implements XMLStreamWriter {
    /** Two spaces, the default value of the {@code indent} constructor parameter. */
    public static final String DEFAULT_INDENT = "  ";
    /** {@code "\n"}, the default value of the {@code newLine} constructor parameter. */
    public static final String DEFAULT_NEW_LINE = "\n";

    private final XMLStreamWriter delegate;
    private final String indent;
    private final String newLine;
    private final AttributeOrder attributeOrder;
    private final Deque<Element > openElements = new ArrayDeque<>();
    private final List<Node > completedTopLevel = new ArrayList<>();
    private Element pendingStart;
    private boolean documentStarted;
    private boolean topLevelContentWritten;

    /**
     * Creates a writer that indents with {@link #DEFAULT_INDENT} and {@link #DEFAULT_NEW_LINE} and
     * does not reorder attributes.
     *
     * @param delegate the writer to which all events are forwarded
     */
    public PrettyXMLStreamWriter( XMLStreamWriter delegate ) {
        this( delegate, DEFAULT_INDENT, DEFAULT_NEW_LINE, null );
    }

    /**
     * Creates a writer with a custom indent and newline string that does not reorder attributes.
     *
     * @param delegate the writer to which all events are forwarded
     * @param indent the string written once per nesting level; may be empty
     * @param newLine the string written before each indented line; may be empty
     */
    public PrettyXMLStreamWriter( XMLStreamWriter delegate, String indent, String newLine ) {
        this( delegate, indent, newLine, null );
    }

    /**
     * @param delegate the writer to which all events are forwarded
     * @param indent the string written once per nesting level; may be empty
     * @param newLine the string written before each indented line; may be empty
     * @param attributeOrder determines, per element, how that element's attributes are ordered;
     *            {@code null} leaves attributes in the order they were written (default)
     */
    public PrettyXMLStreamWriter( XMLStreamWriter delegate, String indent, String newLine, AttributeOrder attributeOrder ) {
        this.delegate = Objects.requireNonNull( delegate, "delegate" );
        this.indent = Objects.requireNonNull( indent, "indent" );
        this.newLine = Objects.requireNonNull( newLine, "newLine" );
        this.attributeOrder = attributeOrder;
    }

    @Override public void writeStartElement( String localName ) throws XMLStreamException {
        start( new QName( localName ), writer -> writer.writeStartElement( localName ) );
    }
    @Override public void writeStartElement( String namespaceURI, String localName ) throws XMLStreamException {
        start( new QName( namespaceURI, localName ), writer -> writer.writeStartElement( namespaceURI, localName ) );
    }
    @Override public void writeStartElement( String prefix, String localName, String namespaceURI ) throws XMLStreamException {
        start( new QName( namespaceURI, localName, prefix ), writer -> writer.writeStartElement( prefix, localName, namespaceURI ) );
    }
    @Override public void writeEmptyElement( String namespaceURI, String localName ) throws XMLStreamException {
        empty( new QName( namespaceURI, localName ), writer -> writer.writeEmptyElement( namespaceURI, localName ) );
    }
    @Override public void writeEmptyElement( String prefix, String localName, String namespaceURI ) throws XMLStreamException {
        empty( new QName( namespaceURI, localName, prefix ), writer -> writer.writeEmptyElement( prefix, localName, namespaceURI ) );
    }
    @Override public void writeEmptyElement( String localName ) throws XMLStreamException {
        empty( new QName( localName ), writer -> writer.writeEmptyElement( localName ) );
    }
    @Override public void writeEndElement() throws XMLStreamException {
        clearPendingStart();
        Element element = openElements.poll();
        if( element == null ) {
            throw new XMLStreamException( "writeEndElement without an open element" );
        }
        addToCurrent( element );
    }
    @Override public void writeEndDocument() throws XMLStreamException {
        closeOpenElements();
        emitCompletedTopLevel();
        delegate.writeEndDocument();
    }
    @Override public void close() throws XMLStreamException { closeOpenElements(); emitCompletedTopLevel(); delegate.close(); }
    @Override public void flush() throws XMLStreamException { emitCompletedTopLevel(); delegate.flush(); }
    @Override public void writeAttribute( String localName, String value ) throws XMLStreamException {
        attribute( new QName( localName ), writer -> writer.writeAttribute( localName, value ) );
    }
    @Override public void writeAttribute( String prefix, String namespaceURI, String localName, String value ) throws XMLStreamException {
        attribute( new QName( namespaceURI, localName, prefix ), writer -> writer.writeAttribute( prefix, namespaceURI, localName, value ) );
    }
    @Override public void writeAttribute( String namespaceURI, String localName, String value ) throws XMLStreamException {
        attribute( new QName( namespaceURI, localName ), writer -> writer.writeAttribute( namespaceURI, localName, value ) );
    }
    @Override public void writeNamespace( String prefix, String namespaceURI ) throws XMLStreamException {
        namespaceDeclaration( writer -> writer.writeNamespace( prefix, namespaceURI ) );
    }
    @Override public void writeDefaultNamespace( String namespaceURI ) throws XMLStreamException {
        namespaceDeclaration( writer -> writer.writeDefaultNamespace( namespaceURI ) );
    }
    @Override public void writeComment( String data ) throws XMLStreamException { leaf( writer -> writer.writeComment( data ), false ); }
    @Override public void writeProcessingInstruction( String target ) throws XMLStreamException { leaf( writer -> writer.writeProcessingInstruction( target ), false ); }
    @Override public void writeProcessingInstruction( String target, String data ) throws XMLStreamException { leaf( writer -> writer.writeProcessingInstruction( target, data ), false ); }
    @Override public void writeCData( String data ) throws XMLStreamException { leaf( writer -> writer.writeCData( data ), true ); }
    @Override public void writeDTD( String dtd ) throws XMLStreamException { leaf( writer -> writer.writeDTD( dtd ), false ); }
    @Override public void writeEntityRef( String name ) throws XMLStreamException { leaf( writer -> writer.writeEntityRef( name ), true ); }
    @Override public void writeStartDocument() throws XMLStreamException { startDocument( writer -> writer.writeStartDocument() ); }
    @Override public void writeStartDocument( String version ) throws XMLStreamException { startDocument( writer -> writer.writeStartDocument( version ) ); }
    @Override public void writeStartDocument( String encoding, String version ) throws XMLStreamException { startDocument( writer -> writer.writeStartDocument( encoding, version ) ); }
    @Override public void writeCharacters( String text ) throws XMLStreamException { leaf( writer -> writer.writeCharacters( text ), true ); }
    @Override public void writeCharacters( char[] text, int start, int len ) throws XMLStreamException {
        char[] copy = new char[len]; System.arraycopy( text, start, copy, 0, len );
        leaf( writer -> writer.writeCharacters( copy, 0, copy.length ), true );
    }
    @Override public String getPrefix( String uri ) throws XMLStreamException { return delegate.getPrefix( uri ); }
    @Override public void setPrefix( String prefix, String uri ) throws XMLStreamException { delegate.setPrefix( prefix, uri ); }
    @Override public void setDefaultNamespace( String uri ) throws XMLStreamException { delegate.setDefaultNamespace( uri ); }
    @Override public void setNamespaceContext( NamespaceContext context ) throws XMLStreamException { delegate.setNamespaceContext( context ); }
    @Override public NamespaceContext getNamespaceContext() { return delegate.getNamespaceContext(); }
    @Override public Object getProperty( String name ) { return delegate.getProperty( name ); }

    private void start( QName name, Action start ) throws XMLStreamException {
        clearPendingStart(); Element element = new Element( name, start, false ); openElements.push( element ); pendingStart = element;
    }
    private void empty( QName name, Action start ) throws XMLStreamException {
        clearPendingStart(); Element element = new Element( name, start, true ); addToCurrent( element ); pendingStart = element;
    }
    private void attribute( QName name, Action action ) throws XMLStreamException {
        if( pendingStart == null ) throw new XMLStreamException( "Attribute without an open start tag" );
        pendingStart.attributes.add( new NamedAction( name, action ) );
    }
    private void namespaceDeclaration( Action action ) throws XMLStreamException {
        if( pendingStart == null ) throw new XMLStreamException( "Namespace declaration without an open start tag" );
        pendingStart.namespaces.add( action );
    }
    private void leaf( Action action, boolean text ) throws XMLStreamException {
        clearPendingStart(); addToCurrent( new Event( action, text ) );
    }
    private void startDocument( Action action ) throws XMLStreamException {
        emitCompletedTopLevel();
        action.write( delegate );
        documentStarted = true;
        topLevelContentWritten = false;
    }
    private void closeOpenElements() throws XMLStreamException {
        while( !openElements.isEmpty() ) {
            writeEndElement();
        }
    }
    private void clearPendingStart() { pendingStart = null; }
    private void addToCurrent( Node node ) { if( openElements.isEmpty() ) completedTopLevel.add( node ); else openElements.peek().content.add( node ); }
    private void emitCompletedTopLevel() throws XMLStreamException {
        for( Node node : completedTopLevel ) {
            beforeTopLevelContent();
            node.write( delegate, 0, false );
            if( node == pendingStart ) clearPendingStart();
        }
        completedTopLevel.clear();
    }
    private void beforeTopLevelContent() throws XMLStreamException {
        if( topLevelContentWritten || documentStarted ) {
            delegate.writeCharacters( newLine );
        }
        topLevelContentWritten = true;
    }
    private void line( XMLStreamWriter writer, int depth ) throws XMLStreamException {
        writer.writeCharacters( newLine ); for( int i = 0; i < depth; i++ ) writer.writeCharacters( indent );
    }

    @FunctionalInterface private interface Action { void write( XMLStreamWriter writer ) throws XMLStreamException; }
    private record NamedAction( QName name, Action action ) {}
    private interface Node { void write( XMLStreamWriter writer, int depth, boolean withinMixedContent ) throws XMLStreamException; boolean isText(); }
    private static final class Event implements Node {
        private final Action action; private final boolean text;
        Event( Action action, boolean text ) { this.action = action; this.text = text; }
        @Override public void write( XMLStreamWriter writer, int depth, boolean withinMixedContent ) throws XMLStreamException { action.write( writer ); }
        @Override public boolean isText() { return text; }
    }
    private final class Element implements Node {
        private final QName name;
        private final Action start; private final boolean empty;
        private final List<Action > namespaces = new ArrayList<>();
        private final List<NamedAction > attributes = new ArrayList<>();
        private final List<Node > content = new ArrayList<>();
        Element( QName name, Action start, boolean empty ) { this.name = name; this.start = start; this.empty = empty; }
        @Override public void write( XMLStreamWriter writer, int depth, boolean withinMixedContent ) throws XMLStreamException {
            start.write( writer );
            for( Action namespace : namespaces ) namespace.write( writer );
            for( NamedAction attribute : sortedAttributes() ) attribute.action().write( writer );
            if( empty ) {
                // A delegate may defer closing an empty element's start tag until its next write
                // call, so a bare flush() right after writeEmptyElement() can leave it unclosed.
                // This forces closure without abandoning the delegate's own self-closing guarantee.
                writer.writeCharacters( "" );
                return;
            }
            boolean mixed = withinMixedContent || content.stream().anyMatch( Node::isText );
            if( mixed ) { for( Node node : content ) node.write( writer, depth + 1, true ); }
            else if( !content.isEmpty() ) {
                for( Node node : content ) { line( writer, depth + 1 ); node.write( writer, depth + 1, false ); }
                line( writer, depth );
            }
            writer.writeEndElement();
        }
        private List<NamedAction > sortedAttributes() {
            Comparator<QName > comparator = (attributeOrder == null) ? null : attributeOrder.comparatorFor( name );
            if( comparator == null ) {
                return attributes;
            }
            List<NamedAction > sorted = new ArrayList<>( attributes );
            sorted.sort( Comparator.comparing( NamedAction::name, comparator ) );
            return sorted;
        }
        @Override public boolean isText() { return false; }
    }
}
