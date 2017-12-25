package org.openhab.habdroid.model;

import net.bytebuddy.implementation.bytecode.Throw;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.concurrent.ExecutionException;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;
import static org.mockito.Mockito.mock;

@RunWith(MockitoJUnitRunner.class)
public class OpenHAB2WidgetTest {
    private OpenHABWidget rootWidget = mock(OpenHAB2Widget.class);

    @Test
    public void testCreateOpenHABWidgetFromJson_createsOpenHAB2Widget() throws Exception {
        OpenHABWidget sut = OpenHAB2Widget.createOpenHABWidgetFromJson(rootWidget, createJSONObject(1), "PNG");
        assertTrue(sut instanceof OpenHAB2Widget);
    }

    @Test
    public void testWithObject1() throws Exception {
        OpenHABWidget sut = OpenHAB2Widget.createOpenHABWidgetFromJson(rootWidget, createJSONObject(1), "PNG");
        assertEquals("icon/groupicon?state=OFF&format=PNG", sut.getIconPath());
        assertEquals("demo11", sut.getChildren().get(0).getId());
        assertEquals(Integer.valueOf(0), sut.getLabelColor());
        assertEquals("M", sut.getPeriod());
        assertEquals(1F, sut.getStep());
        assertEquals(1000, sut.getRefresh());
        assertEquals(true, sut.hasChildren());
        assertEquals(true, sut.hasItem());
        assertEquals(true, sut.hasLinkedPage());
        assertEquals(true, sut.hasMappings());
        assertEquals(false, sut.childrenHasLinkedPages());
        assertEquals(true, sut.childrenHasNonlinkedPages());
    }

    @Test
    public void testWithObject2() throws Exception {
        OpenHABWidget sut = OpenHAB2Widget.createOpenHABWidgetFromJson(rootWidget, createJSONObject(2), "SVG");
        assertEquals("icon/groupicon?state=ON&format=SVG", sut.getIconPath());
        IndexOutOfBoundsException e = null;
        try {
            sut.getChildren().get(0).getId();
        } catch (IndexOutOfBoundsException ex) {
            e = ex;
        }
        assertTrue(e != null);
        Integer i = new Integer(0);
        assertEquals(i, sut.getLabelColor()); //TODO why does this work???
        assertEquals(i, sut.getIconColor());
        assertEquals(i, sut.getValueColor());
        assertEquals("D", sut.getPeriod()); // Object has no period, should default to D
        assertEquals(0.1F, sut.getStep());
        assertEquals(100, sut.getRefresh()); // Min refresh is 100, object has set refresh to 10
        assertEquals(false, sut.hasChildren());
        assertEquals(true, sut.hasItem());
        assertEquals(false, sut.hasLinkedPage());
        assertEquals(true, sut.hasMappings());
        assertEquals(false, sut.childrenHasLinkedPages());
        assertEquals(false, sut.childrenHasNonlinkedPages());
    }

    /**
     * @param id get different json objects depending on the id
     *           1: Default, all values are set
     *           2: Different colors, no periode, max step size < 1
     * @return JSON object
     * @throws Exception
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
                        "    \"iconcolor\": \"white\",\n" +
                        "    \"labelcolor\": \"white\",\n" +
                        "    \"valuecolor\": \"white\",\n" +
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
                        "    \"minValue\": \"0.0\",\n" +
                        "    \"maxValue\": \"10.0\",\n" +
                        "    \"step\": \"0.1\",\n" +
                        "    \"refresh\": \"10\",\n" +
                        "    \"service\": \"D\",\n" +
                        "    \"height\": \"10\",\n" +
                        "    \"iconcolor\": \"orange\",\n" +
                        "    \"labelcolor\": \"blue\",\n" +
                        "    \"valuecolor\": \"red\",\n" +
                        "    \"mappings\": [{\n" +
                        "      \"command\": \"ON\",\n" +
                        "      \"label\": \"On\"\n" +
                        "    }],\n" +
                        "    \"item\": {\n" +
                        "      \"type\": \"Switch\",\n" +
                        "      \"name\": \"switch1\",\n" +
                        "      \"state\": \"ON\",\n" +
                        "      \"link\": \"http://localhost/rest/items/switch1\"\n" +
                        "    },\n" +
                        "  }";
                break;
                default:
                    throw new InvalidParameterException();
        }
        return new JSONObject(json);
    }
}
