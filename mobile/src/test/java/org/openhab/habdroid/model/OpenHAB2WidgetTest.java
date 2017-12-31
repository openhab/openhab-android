package org.openhab.habdroid.model;

import net.bytebuddy.implementation.bytecode.Throw;

import org.json.JSONObject;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.concurrent.ExecutionException;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;
import static org.mockito.Mockito.mock;

import android.graphics.Color;
import static org.hamcrest.CoreMatchers.equalTo;

@RunWith(MockitoJUnitRunner.class)
public class OpenHAB2WidgetTest {
    private OpenHABWidget rootWidget = mock(OpenHAB2Widget.class);
    OpenHABWidget sut1;
    OpenHABWidget sut2;
    OpenHABWidget sut3;

    @Test
    @Before
    public void testCreateOpenHABWidgetFromJson_createsOpenHAB2Widget() throws Exception {
        sut1 = OpenHAB2Widget.createOpenHABWidgetFromJson(rootWidget, createJSONObject(1), "PNG");
        sut2 = OpenHAB2Widget.createOpenHABWidgetFromJson(rootWidget, createJSONObject(2), "SVG");
        sut3 = OpenHAB2Widget.createOpenHABWidgetFromJson(rootWidget, createJSONObject(3), "SVG");
    }

    @Test
    public void testSutInstanceOfOpenHAB2Widget() throws Exception {
        assertTrue(sut1 instanceof OpenHAB2Widget);
        assertTrue(sut2 instanceof OpenHAB2Widget);
        assertTrue(sut3 instanceof OpenHAB2Widget);
    }

    @Test
    public void testGetIconPath() throws Exception {
        assertEquals("icon/groupicon?state=OFF&format=PNG", sut1.getIconPath());
        assertEquals("icon/groupicon?state=ON&format=SVG", sut2.getIconPath());
        assertEquals("icon/slider?state=81&format=SVG", sut3.getChildren().get(0).getIconPath());
        assertEquals("Rollersutter icon should always be 0 to 100, not ON/OFF", "icon/rollershutter?state=0&format=SVG", sut3.getChildren().get(1).getIconPath());
        assertEquals("icon/rollershutter?state=42&format=SVG", sut3.getChildren().get(2).getIconPath());
    }

    @Test
    public void testGetChildren() throws Exception {
        assertEquals("demo11", sut1.getChildren().get(0).getId());
        assertEquals("0202_0_0_1", sut3.getChildren().get(1).getId());
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void testGetChildrenNoChildren() throws Exception {
        sut2.getChildren().get(0).getId();
    }

    @Test
    public void testHasChildren() throws Exception {
        assertEquals(true, sut1.hasChildren());
        assertEquals(false, sut2.hasChildren());
        assertEquals(true, sut3.hasChildren());
    }

    @Test
    public void testGetPeriode() throws Exception {
        assertEquals("M", sut1.getPeriod());
        assertEquals("Object has no period, should default to D", "D", sut2.getPeriod());
    }

    @Test
    public void testGetStep() throws Exception {
        assertEquals(1F, sut1.getStep());
        assertEquals(0.1F, sut2.getStep());
    }

    @Test
    public void testGetRefresh() throws Exception {
        assertEquals(1000, sut1.getRefresh());
        assertEquals("Min refresh is 100, object has set refresh to 10",100, sut2.getRefresh());
    }

    @Test
    public void testHasItem() throws Exception {
        assertEquals(true, sut1.hasItem());
        assertEquals(true, sut2.hasItem());
    }

    @Test
    public void testHasLinkedPage() throws Exception {
        assertEquals(true, sut1.hasLinkedPage());
        assertEquals(false, sut2.hasLinkedPage());
    }

    @Test
    public void testHasMappings() throws Exception {
        assertEquals(true, sut1.hasMappings());
        assertEquals(true, sut2.hasMappings());
    }

    @Test
    public void testChildrenHasLinkedPages() throws Exception {
        assertEquals(false, sut1.childrenHasLinkedPages());
        assertEquals(false, sut2.childrenHasLinkedPages());
        assertEquals(true, sut3.childrenHasLinkedPages());
    }

    @Test
    public void testChildrenHasNonlinkedPages() throws Exception {
        assertEquals(true, sut1.childrenHasNonlinkedPages());
        assertEquals(false, sut2.childrenHasNonlinkedPages());
    }

    @Test
    public void testGetColors() throws Exception {
        Assume.assumeThat(Color.parseColor("blue"), equalTo(Color.BLUE));
        // Doesn't seem to work via Android Studio
        Integer i = new Integer(0);
        assertEquals(i, sut1.getLabelColor());
        assertEquals(i, sut1.getIconColor());
        assertEquals(i, sut1.getValueColor());
        assertEquals(i, sut2.getLabelColor());
        assertEquals(i, sut2.getIconColor());
        assertEquals(i, sut2.getValueColor());
    }

    @Test
    public void testGetType() throws Exception {
        assertEquals("Group", sut1.getType());
        assertEquals("Group", sut2.getType());
        assertEquals("Frame", sut3.getType());
        assertEquals("Switch", sut3.getChildren().get(1).getType());
        assertEquals("Group", sut3.getChildren().get(2).getType());
    }

    @Test
    public void testGetLabel() throws Exception {
        assertEquals("Group1", sut1.getLabel());
        assertEquals("Group1", sut2.getLabel());
        assertEquals("Dimmer [81 %]", sut3.getChildren().get(0).getLabel());
    }

    @Test
    public void testGetMapping() throws Exception {
        assertEquals("ON", sut1.getMapping(0).getCommand());
        assertEquals("On", sut1.getMapping(0).getLabel());
        assertEquals("abcäöüßẞèéóò\uD83D\uDE03", sut2.getMapping(0).getLabel());
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void testGetMappingNoMapping() throws Exception {
        sut3.getChildren().get(0).getMapping(0);
    }

    @Test
    public void testGetMappings() throws Exception {
        assertEquals("ON", sut1.getMappings().get(0).getCommand());
        assertEquals("On", sut1.getMappings().get(0).getLabel());
        assertEquals("abcäöüßẞèéóò\uD83D\uDE03", sut2.getMappings().get(0).getLabel());
    }

    @Test
    public void testAddMappingsAndMappingsSize() throws Exception {
        OpenHABWidgetMapping wm = new OpenHABWidgetMapping("ON", "ABC");
        assertEquals(1, sut1.getMappings().size());
        sut1.getMappings().add(wm);
        assertEquals("ABC", sut1.getMappings().get(1).getLabel());
        assertEquals(2, sut1.getMappings().size());
    }


    @Test
    public void testGetMinValue() throws Exception {
        assertEquals(0.0F, sut1.getMinValue());
        assertEquals(99.7F, sut2.getMinValue());
    }

    @Test
    public void testGetMaxValue() throws Exception {
        assertEquals(10.0F, sut1.getMaxValue());
        assertEquals(-10.0F, sut2.getMaxValue());
    }

    @Test
    public void testGetUrl() throws Exception {
        assertEquals("http://localhost/url", sut1.getUrl());
        assertEquals("http://localhost/url", sut2.getUrl());
        assertEquals(null, sut3.getChildren().get(0).getUrl());
    }

    @Test
    public void testGetLegend() throws Exception {
        assertEquals(new Boolean(true), sut1.getLegend());
        assertEquals(new Boolean(false), sut2.getLegend());
        assertEquals(null, sut3.getChildren().get(0).getLegend());
    }

    @Test
    public void testGetHeight() throws Exception {
        assertEquals(10, sut1.getHeight());
        assertEquals(42, sut2.getHeight());
    }

    @Test
    public void testGetService() throws Exception {
        assertEquals("D", sut1.getService());
        assertEquals("XYZ", sut2.getService());
    }

    @Test
    public void testGetId() throws Exception {
        assertEquals("demo", sut1.getId());
        assertEquals("demo", sut2.getId());
    }

    @Test
    public void testGetEncoding() throws Exception {
        assertEquals("mpeg", sut1.getEncoding());
        assertEquals(null, sut2.getEncoding());
    }

    @Test
    public void testGetState() throws Exception {
        assertEquals(null, sut1.getState());
        assertEquals(null, sut2.getState());
        assertEquals(null, sut3.getChildren().get(0).getState());
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
                        "    \"widgets\": [{ \"widgetId\": \"demo11\" }]\n" +
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
