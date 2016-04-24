package org.openhab.habdroid.model;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

import java.io.StringReader;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;
import static org.mockito.Mockito.mock;

@RunWith(MockitoJUnitRunner.class)
public class OpenHAB1WidgetTest {
    OpenHABWidget rootWidget = mock(OpenHAB1Widget.class);

    @Test
    public void createOpenHABWidgetFromNode_createsOpenHAB1Widget() throws Exception {
        OpenHABWidget sut = OpenHAB1Widget.createOpenHABWidgetFromNode(rootWidget, createXmlNode());
        assertTrue(sut instanceof OpenHAB1Widget);
    }

    @Test
    public void getIconPath_iconExists_returnIconUrlfromImages() throws Exception {
        OpenHABWidget sut = OpenHAB1Widget.createOpenHABWidgetFromNode(rootWidget, createXmlNode());
        assertEquals("images/groupicon.png", sut.getIconPath());
    }

    private Node createXmlNode() throws Exception {
        String xml = "" +
                "<widget>" +
                "  <widgetId>demo</widgetId>" +
                "  <type>Group</type>" +
                "  <label>Group1</label>" +
                "  <icon>groupicon</icon>" +
                "  <url>http://localhost/url</url>" +
                "  <minValue>0.0</minValue>" +
                "  <maxValue>10.0</maxValue>" +
                "  <step>1</step>" +
                "  <refresh>10</refresh>" +
                "  <period>D</period>" +
                "  <service>D</service>" +
                "  <height>10</height>" +
                "  <iconcolor>white</iconcolor>" +
                "  <labelcolor>white</labelcolor>" +
                "  <valuecolor>white</valuecolor>" +
                "  <encoding></encoding>" +
                "  <mapping>" +
                "    <command>ON</command>\n" +
                "    <label>On</label>" +
                "  </mapping>" +
                "  <item>" +
                "    <type>GroupItem</type>" +
                "    <name>group1</name>" +
                "    <state>Undefined</state>" +
                "    <link>http://localhost/rest/items/group1</link>" +
                "  </item>" +
                "  <linkedPage>" +
                "    <id>0001</id>" +
                "    <title>LinkedPage</title>" +
                "    <icon>linkedpageicon</icon>" +
                "    <link>http://localhost/rest/sitemaps/demo/0001</link>" +
                "    <leaf>false</leaf>" +
                "  </linkedPage>" +
                "  <widget>" +
                "    <widgetId>demo11</widgetId>" +
                "  </widget>" +
                "</widget>";
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = dbf.newDocumentBuilder();
        Document document = builder.parse(new InputSource(new StringReader(xml)));
        Node rootNode = document.getFirstChild();
        return rootNode;
    }
}
