package org.openhab.habdroid.model

import junit.framework.Assert.*
import org.json.JSONObject
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.runners.MockitoJUnitRunner
import org.w3c.dom.Node
import org.xml.sax.InputSource
import java.io.StringReader
import java.security.InvalidParameterException
import javax.xml.parsers.DocumentBuilderFactory

@RunWith(MockitoJUnitRunner::class)
class WidgetTest {
    private lateinit var sutXml: List<Widget>
    private lateinit var sut1: List<Widget>
    private lateinit var sut2: List<Widget>
    private lateinit var sut3: List<Widget>

    @Test
    @Before
    @Throws(Exception::class)
    fun parse_createsWidget() {
        sutXml = createXmlNode().collectWidgets(null)
        sut1 = createJsonObject(1).collectWidgets(null, "PNG")
        sut2 = createJsonObject(2).collectWidgets(null, "SVG")
        sut3 = createJsonObject(3).collectWidgets(null, "SVG")
    }

    @Test
    fun testCountInstances() {
        assertEquals(sutXml.size, 2)
        assertEquals(sut1.size, 2)
        assertEquals(sut2.size, 1)
        assertEquals(sut3.size, 4)
    }

    @Test
    fun getIconPath_iconExists_returnIconUrlfromImages() {
        assertEquals("images/groupicon.png", sutXml[0].iconPath)
    }

    @Test
    fun testGetIconPath() {
        assertEquals("icon/groupicon?state=OFF&format=PNG", sut1[0].iconPath)
        assertEquals("icon/groupicon?state=ON&format=SVG", sut2[0].iconPath)
        assertEquals("icon/slider?state=81&format=SVG", sut3[1].iconPath)
        assertEquals("Rollersutter icon must always be 0 to 100, not ON/OFF",
                "icon/rollershutter?state=0&format=SVG", sut3[2].iconPath)
        assertEquals("icon/rollershutter?state=42&format=SVG", sut3[3].iconPath)
    }

    @Test
    fun testGetChildren() {
        assertEquals("demo11", sut1[1].id)
        assertEquals("0202_0_0_1", sut3[2].id)
    }

    @Test
    fun testGetPeriod() {
        assertEquals("M", sut1[0].period)
        assertEquals("Object has no period, should default to 'D'", "D", sut2[0].period)
    }

    @Test
    fun testGetStep() {
        assertEquals(1f, sut1[0].step)
        // this is invalid in JSON (< 0), expected to be adjusted
        assertEquals(0.1f, sut2[0].step)
    }

    @Test
    fun testGetRefresh() {
        assertEquals(1000, sut1[0].refresh)
        assertEquals("Min refresh is 100, object has set refresh to 10",
                100, sut2[0].refresh)
        assertEquals("Missing refresh should equal 0", 0, sut3[0].refresh)
    }

    @Test
    fun testHasItem() {
        assertNotNull(sut1[0].item)
        assertNotNull(sut2[0].item)
    }

    @Test
    fun testHasLinkedPage() {
        assertNotNull(sut1[0].linkedPage)
        assertNull(sut2[0].linkedPage)
    }

    @Test
    fun testHasMappings() {
        assertEquals(true, sut1[0].hasMappings())
        assertEquals(true, sut2[0].hasMappings())
    }

    @Test
    fun testGetColors() {
        assertEquals("white", sut1[0].iconColor)
        assertEquals("#ff0000", sut1[0].labelColor)
        assertEquals("#00ffff", sut1[0].valueColor)
        assertEquals("orange", sut2[0].iconColor)
        assertEquals("blue", sut2[0].labelColor)
        assertEquals("red", sut2[0].valueColor)
    }

    @Test
    fun testGetType() {
        assertEquals(Widget.Type.Group, sut1[0].type)
        assertEquals(Widget.Type.Group, sut2[0].type)
        assertEquals(Widget.Type.Frame, sut3[0].type)
        assertEquals(Widget.Type.Switch, sut3[2].type)
        assertEquals(Widget.Type.Group, sut3[3].type)
    }

    @Test
    fun testGetLabel() {
        assertEquals("Group1", sut1[0].label)
        assertEquals("Group1", sut2[0].label)
        assertEquals("Dimmer [81 %]", sut3[1].label)
    }

    @Test
    @Throws(Exception::class)
    fun testGetMappings() {
        assertEquals("ON", sut1[0].mappings[0].value)
        assertEquals("On", sut1[0].mappings[0].label)
        assertEquals("abcäöüßẞèéóò\uD83D\uDE03", sut2[0].mappings[0].label)
    }

    @Test(expected = IndexOutOfBoundsException::class)
    fun testGetMappingNoMapping() {
        sut3[1].mappings[0]
    }

    @Test
    fun testGetMinValue() {
        assertEquals(0.0f, sut1[0].minValue)
        assertEquals(99.7f, sut2[0].minValue)
    }

    @Test
    fun testGetMaxValue() {
        assertEquals(10.0f, sut1[0].maxValue)
        // this is invalid in JSON (max < min), expected to be adjusted
        assertEquals(99.7f, sut2[0].maxValue)
    }

    @Test
    fun testGetUrl() {
        assertEquals("http://localhost/url", sut1[0].url)
        assertEquals("http://localhost/url", sut2[0].url)
        assertEquals(null, sut3[1].url)
    }

    @Test
    fun testGetLegend() {
        assertEquals(true, sut1[0].legend)
        assertEquals(false, sut2[0].legend)
        assertEquals(null, sut3[1].legend)
    }

    @Test
    fun testGetHeight() {
        assertEquals(10, sut1[0].height)
        assertEquals(42, sut2[0].height)
    }

    @Test
    fun testGetService() {
        assertEquals("D", sut1[0].service)
        assertEquals("XYZ", sut2[0].service)
    }

    @Test
    fun testGetId() {
        assertEquals("demo", sut1[0].id)
        assertEquals("demo", sut2[0].id)
    }

    @Test
    fun testGetEncoding() {
        assertEquals("mpeg", sut1[0].encoding)
        assertEquals(null, sut2[0].encoding)
    }

    @Throws(Exception::class)
    private fun createXmlNode(): Node {
        val xml = ("<widget>"
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
                + "</widget>")
        val dbf = DocumentBuilderFactory.newInstance()
        val builder = dbf.newDocumentBuilder()
        val document = builder.parse(InputSource(StringReader(xml)))
        return document.firstChild
    }

    /**
     * @param id get different json objects depending on the id
     * 1: All values are set
     * 2: Different colors, no periode, max step size < 1, chart refresh < 100
     * 3: Frame with Slider, Rollershutter switch and Rollershutter group
     *
     * @return JSON object
     * @throws Exception when no object with id is found
     */
    @Throws(Exception::class)
    private fun createJsonObject(id: Int): JSONObject {
        val json: String
        when (id) {
            1 -> json = ("{\n"
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
                    + "  }")
            2 -> json = ("{\n"
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
                    + "  }")
            3 -> json = ("{\"widgetId\": \"0202_0\","
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
                    + "}")
            else -> throw InvalidParameterException()
        }
        return JSONObject(json)
    }
}
