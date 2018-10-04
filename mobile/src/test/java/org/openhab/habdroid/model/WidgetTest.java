package org.openhab.habdroid.model;

import org.json.JSONObject;
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

@RunWith(MockitoJUnitRunner.class)
public class WidgetTest {
    private List<Widget> mSutXml = new ArrayList<>();
    private List<Widget> mSut1 = new ArrayList<>();
    private List<Widget> mSut2 = new ArrayList<>();
    private List<Widget> mSut3 = new ArrayList<>();

    @Test
    @Before
    public void parse_createsWidget() throws Exception {
        Widget.parseXml(mSutXml, null, createXmlNode());
        Widget.parseJson(mSut1, null, createJsonObject(1), "PNG");
        Widget.parseJson(mSut2, null, createJsonObject(2), "SVG");
        Widget.parseJson(mSut3, null, createJsonObject(3), "SVG");
    }

    @Test
    public void testCountInstances() {
        assertEquals(mSutXml.size(), 2);
        assertEquals(mSut1.size(), 2);
        assertEquals(mSut2.size(), 1);
        assertEquals(mSut3.size(), 4);
    }

    @Test
    public void getIconPath_iconExists_returnIconUrlfromImages() {
        assertEquals("images/groupicon.png", mSutXml.get(0).iconPath());
    }

    @Test
    public void testGetIconPath() {
        assertEquals("icon/groupicon?state=OFF&format=PNG", mSut1.get(0).iconPath());
        assertEquals("icon/groupicon?state=ON&format=SVG", mSut2.get(0).iconPath());
        assertEquals("icon/slider?state=81&format=SVG", mSut3.get(1).iconPath());
        assertEquals("Rollersutter icon must always be 0 to 100, not ON/OFF",
                "icon/rollershutter?state=0&format=SVG", mSut3.get(2).iconPath());
        assertEquals("icon/rollershutter?state=42&format=SVG", mSut3.get(3).iconPath());
    }

    @Test
    public void testGetChildren() {
        assertEquals("demo11", mSut1.get(1).id());
        assertEquals("0202_0_0_1", mSut3.get(2).id());
    }

    @Test
    public void testGetPeriod() {
        assertEquals("M", mSut1.get(0).period());
        assertEquals("Object has no period, should default to 'D'", "D", mSut2.get(0).period());
    }

    @Test
    public void testGetStep() {
        assertEquals(1F, mSut1.get(0).step());
        // this is invalid in JSON (< 0), expected to be adjusted
        assertEquals(0.1F, mSut2.get(0).step());
    }

    @Test
    public void testGetRefresh() {
        assertEquals(1000, mSut1.get(0).refresh());
        assertEquals("Min refresh is 100, object has set refresh to 10",
                100, mSut2.get(0).refresh());
        assertEquals("Missing refresh should equal 0", 0, mSut3.get(0).refresh());
    }

    @Test
    public void testHasItem() {
        assertNotNull(mSut1.get(0).item());
        assertNotNull(mSut2.get(0).item());
    }

    @Test
    public void testHasLinkedPage() {
        assertNotNull(mSut1.get(0).linkedPage());
        assertNull(mSut2.get(0).linkedPage());
    }

    @Test
    public void testHasMappings() {
        assertEquals(true, mSut1.get(0).hasMappings());
        assertEquals(true, mSut2.get(0).hasMappings());
    }

    @Test
    public void testGetColors() {
        assertEquals("white", mSut1.get(0).iconColor());
        assertEquals("#ff0000", mSut1.get(0).labelColor());
        assertEquals("#00ffff", mSut1.get(0).valueColor());
        assertEquals("orange", mSut2.get(0).iconColor());
        assertEquals("blue", mSut2.get(0).labelColor());
        assertEquals("red", mSut2.get(0).valueColor());
    }

    @Test
    public void testGetType() {
        assertEquals(Widget.Type.Group, mSut1.get(0).type());
        assertEquals(Widget.Type.Group, mSut2.get(0).type());
        assertEquals(Widget.Type.Frame, mSut3.get(0).type());
        assertEquals(Widget.Type.Switch, mSut3.get(2).type());
        assertEquals(Widget.Type.Group, mSut3.get(3).type());
    }

    @Test
    public void testGetLabel() {
        assertEquals("Group1", mSut1.get(0).label());
        assertEquals("Group1", mSut2.get(0).label());
        assertEquals("Dimmer [81 %]", mSut3.get(1).label());
    }

    @Test
    public void testGetMappings() throws Exception {
        assertEquals("ON", mSut1.get(0).mappings().get(0).value());
        assertEquals("On", mSut1.get(0).mappings().get(0).label());
        assertEquals("abcäöüßẞèéóò\uD83D\uDE03", mSut2.get(0).mappings().get(0).label());
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void testGetMappingNoMapping() {
        mSut3.get(1).mappings().get(0);
    }

    @Test
    public void testGetMinValue() {
        assertEquals(0.0F, mSut1.get(0).minValue());
        assertEquals(99.7F, mSut2.get(0).minValue());
    }

    @Test
    public void testGetMaxValue() {
        assertEquals(10.0F, mSut1.get(0).maxValue());
        // this is invalid in JSON (max < min), expected to be adjusted
        assertEquals(99.7F, mSut2.get(0).maxValue());
    }

    @Test
    public void testGetUrl() {
        assertEquals("http://localhost/url", mSut1.get(0).url());
        assertEquals("http://localhost/url", mSut2.get(0).url());
        assertEquals(null, mSut3.get(1).url());
    }

    @Test
    public void testGetLegend() {
        assertEquals(new Boolean(true), mSut1.get(0).legend());
        assertEquals(new Boolean(false), mSut2.get(0).legend());
        assertEquals(null, mSut3.get(1).legend());
    }

    @Test
    public void testGetHeight() {
        assertEquals(10, mSut1.get(0).height());
        assertEquals(42, mSut2.get(0).height());
    }

    @Test
    public void testGetService() {
        assertEquals("D", mSut1.get(0).service());
        assertEquals("XYZ", mSut2.get(0).service());
    }

    @Test
    public void testGetId() {
        assertEquals("demo", mSut1.get(0).id());
        assertEquals("demo", mSut2.get(0).id());
    }

    @Test
    public void testGetEncoding() {
        assertEquals("mpeg", mSut1.get(0).encoding());
        assertEquals(null, mSut2.get(0).encoding());
    }

    private Node createXmlNode() throws Exception {
        String xml = "<widget>"
                + "  <widgetId>demo</widgetId>"
                + "  <type>Group</type>"
                + "  <label>Group1</label>"
                + "  <icon>groupicon</icon>"
                + "  <url>http://localhost/url</url>"
                + "  <minValue>0.0</minValue>"
                + "  <maxValue>10.0</maxValue>"
                + "  <step>1</step>"
                + "  <refresh>10</refresh>"
                + "  <period>D</period>"
                + "  <service>D</service>"
                + "  <height>10</height>"
                + "  <iconcolor>white</iconcolor>"
                + "  <labelcolor>white</labelcolor>"
                + "  <valuecolor>white</valuecolor>"
                + "  <encoding></encoding>"
                + "  <mapping>"
                + "    <command>ON</command>\n"
                + "    <label>On</label>"
                + "  </mapping>"
                + "  <item>"
                + "    <type>GroupItem</type>"
                + "    <name>group1</name>"
                + "    <state>Undefined</state>"
                + "    <link>http://localhost/rest/items/group1</link>"
                + "  </item>"
                + "  <linkedPage>"
                + "    <id>0001</id>"
                + "    <title>LinkedPage</title>"
                + "    <icon>linkedpageicon</icon>"
                + "    <link>http://localhost/rest/sitemaps/demo/0001</link>"
                + "    <leaf>false</leaf>"
                + "  </linkedPage>"
                + "  <widget>"
                + "    <widgetId>demo11</widgetId>"
                + "    <type>Switch</type>"
                + "  </widget>"
                + "</widget>";
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = dbf.newDocumentBuilder();
        Document document = builder.parse(new InputSource(new StringReader(xml)));
        return document.getFirstChild();
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
    private JSONObject createJsonObject(int id) throws Exception {
        String json;
        switch (id) {
            case 1:
                json = "{\n"
                        + "    \"widgetId\": \"demo\",\n"
                        + "    \"type\": \"Group\",\n"
                        + "    \"label\": \"Group1\",\n"
                        + "    \"icon\": \"groupicon\",\n"
                        + "    \"url\": \"http://localhost/url\",\n"
                        + "    \"minValue\": \"0.0\",\n"
                        + "    \"maxValue\": \"10.0\",\n"
                        + "    \"step\": \"1\",\n"
                        + "    \"refresh\": \"1000\",\n"
                        + "    \"period\": \"M\",\n"
                        + "    \"service\": \"D\",\n"
                        + "    \"height\": \"10\",\n"
                        + "    \"legend\": \"true\",\n"
                        + "    \"iconcolor\": \"white\",\n"
                        + "    \"labelcolor\": \"#ff0000\",\n"
                        + "    \"valuecolor\": \"#00ffff\",\n"
                        + "    \"encoding\": \"mpeg\",\n"
                        + "    \"mappings\": [{\n"
                        + "      \"command\": \"ON\",\n"
                        + "      \"label\": \"On\"\n"
                        + "    }],\n"
                        + "    \"item\": {\n"
                        + "      \"type\": \"GroupItem\",\n"
                        + "      \"name\": \"group1\",\n"
                        + "      \"state\": \"OFF\",\n"
                        + "      \"link\": \"http://localhost/rest/items/group1\"\n"
                        + "    },\n"
                        + "    \"linkedPage\": {\n"
                        + "      \"id\": \"0001\",\n"
                        + "      \"title\": \"LinkedPage\",\n"
                        + "      \"icon\": \"linkedpageicon\",\n"
                        + "      \"link\": \"http://localhost/rest/sitemaps/demo/0001\",\n"
                        + "      \"leaf\": \"false\"\n"
                        + "    },\n"
                        + "    \"widgets\": [{ \"widgetId\": \"demo11\", \"type\": \"Switch\" }]\n"
                        + "  }";
                break;
            case 2:
                json = "{\n"
                        + "    \"widgetId\": \"demo\",\n"
                        + "    \"type\": \"Group\",\n"
                        + "    \"label\": \"Group1\",\n"
                        + "    \"icon\": \"groupicon\",\n"
                        + "    \"url\": \"http://localhost/url\",\n"
                        + "    \"minValue\": \"99.7\",\n"
                        + "    \"maxValue\": \"-10.0\",\n"
                        + "    \"step\": \"-0.1\",\n"
                        + "    \"refresh\": \"10\",\n"
                        + "    \"service\": \"XYZ\",\n"
                        + "    \"legend\": \"false\",\n"
                        + "    \"height\": \"42\",\n"
                        + "    \"iconcolor\": \"orange\",\n"
                        + "    \"labelcolor\": \"blue\",\n"
                        + "    \"valuecolor\": \"red\",\n"
                        + "    \"mappings\": [{\n"
                        + "      \"command\": \"ON\",\n"
                        + "      \"label\": \"abcäöüßẞèéóò\uD83D\uDE03\"\n"
                        + "    }],\n"
                        + "    \"item\": {\n"
                        + "      \"type\": \"Switch\",\n"
                        + "      \"name\": \"switch1\",\n"
                        + "      \"state\": \"ON\",\n"
                        + "      \"link\": \"http://localhost/rest/items/switch1\"\n"
                        + "    },\n"
                        + "  }";
                break;
            case 3:
                json = "{\"widgetId\": \"0202_0\","
                        + "\"type\": \"Frame\","
                        + "\"label\": \"Percent-based Widgets\","
                        + "\"icon\": \"frame\","
                        + "\"mappings\": [],"
                        + "\"widgets\": ["
                        + "{"
                        + "\"widgetId\": \"0202_0_0\","
                        + "\"type\": \"Slider\","
                        + "\"label\": \"Dimmer [81 %]\","
                        + "\"icon\": \"slider\","
                        + "\"mappings\": [],"
                        + "\"switchSupport\": true,"
                        + "\"sendFrequency\": 0,"
                        + "\"item\": {"
                        + "\"link\": \"http://openhab.local:8080/rest/items/DimmedLight\","
                        + "\"state\": \"81\","
                        + "\"stateDescription\": {"
                        + "\"pattern\": \"%d %%\","
                        + "\"readOnly\": false,"
                        + "\"options\": []"
                        + "},"
                        + "\"type\": \"Dimmer\","
                        + "\"name\": \"DimmedLight\","
                        + "\"label\": \"Dimmer\","
                        + "\"category\": \"slider\","
                        + "\"tags\": [],"
                        + "\"groupNames\": []"
                        + "},"
                        + "\"widgets\": []"
                        + "},"
                        + "{"
                        + "\"widgetId\": \"0202_0_0_1\","
                        + "\"type\": \"Switch\","
                        + "\"label\": \"Roller Shutter\","
                        + "\"icon\": \"rollershutter\","
                        + "\"mappings\": [],"
                        + "\"item\": {"
                        + "\"link\": \"http://openhab.local:8080/rest/items/DemoShutter\","
                        + "\"state\": \"0\","
                        + "\"type\": \"Rollershutter\","
                        + "\"name\": \"DemoShutter\","
                        + "\"label\": \"Roller Shutter\","
                        + "\"tags\": [],"
                        + "\"groupNames\": ["
                        + "\"Shutters\""
                        + "]"
                        + "},"
                        + "\"widgets\": []"
                        + "},"
                        + "{"
                        + "\"widgetId\": \"0202_0_0_1_2\","
                        + "\"type\": \"Group\","
                        + "\"label\": \"Shutters\","
                        + "\"icon\": \"rollershutter\","
                        + "\"mappings\": [],"
                        + "\"item\": {"
                        + "\"members\": [],"
                        + "\"groupType\": \"Rollershutter\","
                        + "\"function\": {"
                        + "\"name\": \"AVG\""
                        + "},"
                        + "\"link\": \"http://openhab.local:8080/rest/items/Shutters\","
                        + "\"state\": \"42\","
                        + "\"type\": \"Group\","
                        + "\"name\": \"Shutters\","
                        + "\"category\": \"rollershutter\","
                        + "\"tags\": [],"
                        + "\"groupNames\": []"
                        + "},"
                        + "\"linkedPage\": {"
                        + "\"id\": \"02020002\","
                        + "\"title\": \"Shutters\","
                        + "\"icon\": \"rollershutter\","
                        + "\"link\": \"http://openhab.local:8080/rest/sitemaps/demo/02020002\","
                        + "\"leaf\": true,"
                        + "\"timeout\": false"
                        + "},"
                        + "\"widgets\": []"
                        + "}"
                        + "]"
                        + "}";
                break;
            default:
                throw new InvalidParameterException();
        }
        return new JSONObject(json);
    }
}
