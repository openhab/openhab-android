package org.openhab.habdroid.model;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;
import static org.mockito.Mockito.mock;

@RunWith(MockitoJUnitRunner.class)
public class OpenHAB2WidgetTest {
    OpenHABWidget rootWidget = mock(OpenHAB2Widget.class);

    @Test
    public void getIconPath_iconExists_returnIconUrlToIconServlet() throws Exception {
        OpenHABWidget sut = OpenHAB2Widget.createOpenHABWidgetFromJson(rootWidget, createJSONObject(), "PNG");
        assertEquals("icon/groupicon?state=OFF&format=PNG", sut.getIconPath());
    }

    @Test
    public void testCreateOpenHABWidgetFromJson_createsOpenHAB2Widget() throws Exception {
        OpenHABWidget sut = OpenHAB2Widget.createOpenHABWidgetFromJson(rootWidget, createJSONObject(), "PNG");
        assertTrue(sut instanceof OpenHAB2Widget);
    }

    private JSONObject createJSONObject() throws Exception {

        String json = "{\n" +
                "    \"widgetId\": \"demo\",\n" +
                "    \"type\": \"Group\",\n" +
                "    \"label\": \"Group1\",\n" +
                "    \"icon\": \"groupicon\",\n" +
                "    \"url\": \"http://localhost/url\",\n" +
                "    \"minValue\": \"0.0\",\n" +
                "    \"maxValue\": \"10.0\",\n" +
                "    \"step\": \"1\",\n" +
                "    \"refresh\": \"10\",\n" +
                "    \"period\": \"D\",\n" +
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
        return new JSONObject(json);
    }
}
