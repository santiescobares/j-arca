package com.github.santiescobares.jarca.soap;

import com.github.santiescobares.jarca.error.ArcaTransportException;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

/**
 * Detects and extracts SOAP 1.1 faults from a parsed response document.
 */
public final class SoapFaultParser {

    private static final String NS_SOAP = "http://schemas.xmlsoap.org/soap/envelope/";

    private SoapFaultParser() {}

    /**
     * Throws {@link ArcaTransportException} if the document contains a SOAP fault.
     */
    public static void throwIfFault(Document doc) {
        NodeList faults = doc.getElementsByTagNameNS(NS_SOAP, "Fault");
        if (faults.getLength() == 0) {
            // also try without namespace (some ARCA responses omit it)
            faults = doc.getElementsByTagName("Fault");
        }
        if (faults.getLength() > 0) {
            org.w3c.dom.Element fault = (org.w3c.dom.Element) faults.item(0);
            String faultString = textContent(fault, "faultstring");
            String faultCode = textContent(fault, "faultcode");
            throw new ArcaTransportException("SOAP fault [" + faultCode + "]: " + faultString);
        }
    }

    private static String textContent(org.w3c.dom.Element parent, String localName) {
        NodeList nl = parent.getElementsByTagName(localName);
        return nl.getLength() > 0 ? nl.item(0).getTextContent().trim() : "(unknown)";
    }
}
