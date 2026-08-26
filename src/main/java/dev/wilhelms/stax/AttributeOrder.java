package dev.wilhelms.stax;

import java.util.Comparator;
import javax.xml.namespace.QName;

/**
 * Determines, per element, how {@link PrettyXMLStreamWriter} orders that element's attributes.
 *
 * <p>Scoped to the element rather than applied globally, so the same attribute can be ordered
 * differently - or left alone - depending on which element it appears on. Both the element and its
 * attributes are identified by {@link QName} (namespace URI + local name), not by local name alone:
 * a plain {@code String} cannot tell apart two elements or attributes that share a local name but
 * differ in namespace (e.g. a plain {@code type} attribute versus {@code xsi:type}).</p>
 */
@FunctionalInterface
public interface AttributeOrder {
    /**
     * @param element the qualified name of the element whose attributes are about to be written
     * @return a comparator over the element's attribute qualified names, or {@code null} to leave
     *         this element's attribute order unchanged
     */
    Comparator<QName > comparatorFor( QName element );
}
