package org.openhab.habdroid.model;

import android.graphics.Color;

import org.json.JSONObject;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

import java.io.StringReader;
import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertNull;
import static junit.framework.Assert.assertTrue;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.mockito.Mockito.mock;

@RunWith(MockitoJUnitRunner.class)
public class OpenHABWidgetTest {
    List<OpenHABWidget> sutXml = new ArrayList<>();
    List<OpenHABWidget> sut1 = new ArrayList<>();
    List<OpenHABWidget> sut2 = new ArrayList<>();
    List<OpenHABWidget> sut3 = new ArrayList<>();

    @Test
    @Before
    public void parse_createsOpenHABWidget() throws Exception {
        OpenHABWidget.parseXml(sutXml, null, createXmlNode());
        OpenHABWidget.parseJson(sut1, null, createJSONObject(1), "PNG");
        OpenHABWidget.parseJson(sut2, null, createJSONObject(2), "SVG");
        OpenHABWidget.parseJson(sut3, null, createJSONObject(3), "SVG");
    }

    @Test
    public void testCountInstances() throws Exception {
        assertEquals(sutXml.size(), 2);
        assertEquals(sut1.size(), 2);
        assertEquals(sut2.size(), 1);
        assertEquals(sut3.size(), 4);
    }

    @Test
    public void getIconPath_iconExists_returnIconUrlfromImages() throws Exception {
        assertEquals("images/groupicon.png", sutXml.get(0).iconPath());
    }

    @Test
    public void testGetIconPath() throws Exception {
        assertEquals("icon/groupicon?state=OFF&format=PNG", sut1.get(0).iconPath());
        assertEquals("icon/groupicon?state=ON&format=SVG", sut2.get(0).iconPath());
        assertEquals("icon/slider?state=81&format=SVG", sut3.get(1).iconPath());
        assertEquals("Rollersutter icon should always be 0 to 100, not ON/OFF", "icon/rollershutter?state=0&format=SVG", sut3.get(2).iconPath());
        assertEquals("icon/rollershutter?state=42&format=SVG", sut3.get(3).iconPath());
    }

    @Test
    public void testGetChildren() throws Exception {
        assertEquals("demo11", sut1.get(1).id());
        assertEquals("0202_0_0_1", sut3.get(2).id());
    }

    @Test
    public void testGetPeriod() throws Exception {
        assertEquals("M", sut1.get(0).period());
        assertEquals("Object has no period, should default to D", "D", sut2.get(0).period());
    }

    @Test
    public void testGetStep() throws Exception {
        assertEquals(1F, sut1.get(0).step());
        assertEquals(0.1F, sut2.get(0).step());
    }

    @Test
    public void testGetRefresh() throws Exception {
        assertEquals(1000, sut1.get(0).refresh());
        assertEquals("Min refresh is 100, object has set refresh to 10",100, sut2.get(0).refresh());
    }

    @Test
    public void testHasItem() throws Exception {
        assertNotNull(sut1.get(0).item());
        assertNotNull(sut2.get(0).item());
    }

    @Test
    public void testHasLinkedPage() throws Exception {
        assertNotNull(sut1.get(0).linkedPage());
        assertNull(sut2.get(0).linkedPage());
    }

    @Test
    public void testHasMappings() throws Exception {
        assertEquals(true, sut1.get(0).hasMappings());
        assertEquals(true, sut2.get(0).hasMappings());
    }

    @Test
    public void testGetColors() throws Exception {
        Assume.assumeThat(Color.parseColor("blue"), equalTo(Color.BLUE));
        // Doesn't seem to work via Android Studio
        Integer i = new Integer(0);
        assertEquals(i, sut1.get(0).labelColor());
        assertEquals(i, sut1.get(0).iconColor());
        assertEquals(i, sut1.get(0).valueColor());
        assertEquals(i, sut2.get(0).labelColor());
        assertEquals(i, sut2.get(0).iconColor());
        assertEquals(i, sut2.get(0).valueColor());
    }

    @Test
    public void testGetType() throws Exception {
        assertEquals("Group", sut1.get(0).type());
        assertEquals("Group", sut2.get(0).type());
        assertEquals("Frame", sut3.get(0).type());
        assertEquals("Switch", sut3.get(2).type());
        assertEquals("Group", sut3.get(3).type());
    }

    @Test
    public void testGetLabel() throws Exception {
        assertEquals("Group1", sut1.get(0).label());
        assertEquals("Group1", sut2.get(0).label());
        assertEquals("Dimmer [81 %]", sut3.get(1).label());
    }

    @Test
    public void testGetMappings() throws Exception {
        assertEquals("ON", sut1.get(0).mappings().get(0).command());
        assertEquals("On", sut1.get(0).mappings().get(0).label());
        assertEquals("abcäöüßẞèéóò\uD83D\uDE03", sut2.get(0).mappings().get(0).label());
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void testGetMappingNoMapping() throws Exception {
        sut3.get(1).mappings().get(0);
    }

    @Test
    public void testGetMinValue() throws Exception {
        assertEquals(0.0F, sut1.get(0).minValue());
        assertEquals(99.7F, sut2.get(0).minValue());
    }

    @Test
    public void testGetMaxValue() throws Exception {
        assertEquals(10.0F, sut1.get(0).maxValue());
        assertEquals(-10.0F, sut2.get(0).maxValue());
    }

    @Test
    public void testGetUrl() throws Exception {
        assertEquals("http://localhost/url", sut1.get(0).url());
        assertEquals("http://localhost/url", sut2.get(0).url());
        assertEquals(null, sut3.get(1).url());
    }

    @Test
    public void testGetLegend() throws Exception {
        assertEquals(new Boolean(true), sut1.get(0).legend());
        assertEquals(new Boolean(false), sut2.get(0).legend());
        assertEquals(null, sut3.get(1).legend());
    }

    @Test
    public void testGetHeight() throws Exception {
        assertEquals(10, sut1.get(0).height());
        assertEquals(42, sut2.get(0).height());
    }

    @Test
    public void testGetService() throws Exception {
        assertEquals("D", sut1.get(0).service());
        assertEquals("XYZ", sut2.get(0).service());
    }

    @Test
    public void testGetId() throws Exception {
        assertEquals("demo", sut1.get(0).id());
        assertEquals("demo", sut2.get(0).id());
    }

    @Test
    public void testGetEncoding() throws Exception {
        assertEquals("mpeg", sut1.get(0).encoding());
        assertEquals(null, sut2.get(0).encoding());
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
                "    <type>Switch</type>" +
                "  </widget>" +
                "</widget>";
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = dbf.newDocumentBuilder();
        Document document = builder.parse(new InputSource(new StringReader(xml)));
        Node rootNode = document.getFirstChild();
        return rootNode;
    }

    /**
     * @param id get different json objects depending on the id
     *           1: All values are set
     *           2: Different colors, no periode, max step size < 1, chart refresh < 100
     *           3: Frame with Slider, Rollershutter switch and Rollershutter group
     *
     * @return JSON object
     * @throws Exception when no object with id is found
     */
    private JSONObject createJSONObject(int id) throws Exception {
        String json;
        switch(id) {
            case 1:
                json = "{\n" +
                        "    \"widgetId\": \"demo\",\n" +
                        "    \"type\": \"Group\",\n" +
                        "    \"label\": \"Group1\",\n" +
                        "    \"icon\": \"groupicon\",\n" +
                        "    \"url\": \"http://localhost/url\",\n" +
                        "    \"minValue\": \"0.0\",\n" +
                        "    \"maxValue\": \"10.0\",\n" +
                        "    \"step\": \"1\",\n" +
                        "    \"refresh\": \"1000\",\n" +
                        "    \"period\": \"M\",\n" +
                        "    \"service\": \"D\",\n" +
                        "    \"height\": \"10\",\n" +
                        "    \"legend\": \"true\",\n" +
                        "    \"iconcolor\": \"white\",\n" +
                        "    \"labelcolor\": \"white\",\n" +
                        "    \"valuecolor\": \"white\",\n" +
                        "    \"encoding\": \"mpeg\",\n" +
                        "    \"mappings\": [{\n" +
                        "      \"command\": \"ON\",\n" +
                        "      \"label\": \"On\"\n" +
                        "    }],\n" +
                        "    \"item\": {\n" +
                        "      \"type\": \"GroupItem\",\n" +
                        "      \"name\": \"group1\",\n" +
                        "      \"state\": \"OFF\",\n" +
                        "      \"link\": \"http://localhost/rest/items/group1\"\n" +
                        "    },\n" +
                        "    \"linkedPage\": {\n" +
                        "      \"id\": \"0001\",\n" +
                        "      \"title\": \"LinkedPage\",\n" +
                        "      \"icon\": \"linkedpageicon\",\n" +
                        "      \"link\": \"http://localhost/rest/sitemaps/demo/0001\",\n" +
                        "      \"leaf\": \"false\"\n" +
                        "    },\n" +
                        "    \"widgets\": [{ \"widgetId\": \"demo11\", \"type\": \"Switch\" }]\n" +
                        "  }";
                break;
            case 2:
                json = "{\n" +
                        "    \"widgetId\": \"demo\",\n" +
                        "    \"type\": \"Group\",\n" +
                        "    \"label\": \"Group1\",\n" +
                        "    \"icon\": \"groupicon\",\n" +
                        "    \"url\": \"http://localhost/url\",\n" +
                        "    \"minValue\": \"99.7\",\n" +
                        "    \"maxValue\": \"-10.0\",\n" +
                        "    \"step\": \"0.1\",\n" +
                        "    \"refresh\": \"10\",\n" +
                        "    \"service\": \"XYZ\",\n" +
                        "    \"legend\": \"false\",\n" +
                        "    \"height\": \"42\",\n" +
                        "    \"iconcolor\": \"orange\",\n" +
                        "    \"labelcolor\": \"blue\",\n" +
                        "    \"valuecolor\": \"red\",\n" +
                        "    \"mappings\": [{\n" +
                        "      \"command\": \"ON\",\n" +
                        "      \"label\": \"abcäöüßẞèéóò\uD83D\uDE03\"\n" +
                        "    }],\n" +
                        "    \"item\": {\n" +
                        "      \"type\": \"Switch\",\n" +
                        "      \"name\": \"switch1\",\n" +
                        "      \"state\": \"ON\",\n" +
                        "      \"link\": \"http://localhost/rest/items/switch1\"\n" +
                        "    },\n" +
                        "  }";
                break;
            case 3:
                json = "{\"widgetId\": \"0202_0\"," +
                        "\"type\": \"Frame\"," +
                        "\"label\": \"Percent-based Widgets\"," +
                        "\"icon\": \"frame\"," +
                        "\"mappings\": []," +
                        "\"widgets\": [" +
                        "{" +
                        "\"widgetId\": \"0202_0_0\"," +
                        "\"type\": \"Slider\"," +
                        "\"label\": \"Dimmer [81 %]\"," +
                        "\"icon\": \"slider\"," +
                        "\"mappings\": []," +
                        "\"switchSupport\": true," +
                        "\"sendFrequency\": 0," +
                        "\"item\": {" +
                        "\"link\": \"http://openhab.local:8080/rest/items/DimmedLight\"," +
                        "\"state\": \"81\"," +
                        "\"stateDescription\": {" +
                        "\"pattern\": \"%d %%\"," +
                        "\"readOnly\": false," +
                        "\"options\": []" +
                        "}," +
                        "\"type\": \"Dimmer\"," +
                        "\"name\": \"DimmedLight\"," +
                        "\"label\": \"Dimmer\"," +
                        "\"category\": \"slider\"," +
                        "\"tags\": []," +
                        "\"groupNames\": []" +
                        "}," +
                        "\"widgets\": []" +
                        "}," +
                        "{" +
                        "\"widgetId\": \"0202_0_0_1\"," +
                        "\"type\": \"Switch\"," +
                        "\"label\": \"Roller Shutter\"," +
                        "\"icon\": \"rollershutter\"," +
                        "\"mappings\": []," +
                        "\"item\": {" +
                        "\"link\": \"http://openhab.local:8080/rest/items/DemoShutter\"," +
                        "\"state\": \"0\"," +
                        "\"type\": \"Rollershutter\"," +
                        "\"name\": \"DemoShutter\"," +
                        "\"label\": \"Roller Shutter\"," +
                        "\"tags\": []," +
                        "\"groupNames\": [" +
                        "\"Shutters\"" +
                        "]" +
                        "}," +
                        "\"widgets\": []" +
                        "}," +
                        "{" +
                        "\"widgetId\": \"0202_0_0_1_2\"," +
                        "\"type\": \"Group\"," +
                        "\"label\": \"Shutters\"," +
                        "\"icon\": \"rollershutter\"," +
                        "\"mappings\": []," +
                        "\"item\": {" +
                        "\"members\": []," +
                        "\"groupType\": \"Rollershutter\"," +
                        "\"function\": {" +
                        "\"name\": \"AVG\"" +
                        "}," +
                        "\"link\": \"http://openhab.local:8080/rest/items/Shutters\"," +
                        "\"state\": \"42\"," +
                        "\"type\": \"Group\"," +
                        "\"name\": \"Shutters\"," +
                        "\"category\": \"rollershutter\"," +
                        "\"tags\": []," +
                        "\"groupNames\": []" +
                        "}," +
                        "\"linkedPage\": {" +
                        "\"id\": \"02020002\"," +
                        "\"title\": \"Shutters\"," +
                        "\"icon\": \"rollershutter\"," +
                        "\"link\": \"http://openhab.local:8080/rest/sitemaps/demo/02020002\"," +
                        "\"leaf\": true," +
                        "\"timeout\": false" +
                        "}," +
                        "\"widgets\": []" +
                        "}" +
                        "]" +
                        "}";
                break;
            default:
                throw new InvalidParameterException();
        }
        return new JSONObject(json);
    }
}
