package net.openan.a2at.sdk.negotiation.generation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import net.openan.a2at.sdk.negotiation.content.NegotiationItem;
import org.junit.jupiter.api.Test;

class NegotiationItemFormatterTest {

    private final NegotiationItemFormatter formatter = new NegotiationItemFormatter();

    @Test
    void formatsNumberedLinesWithSuppliedColonPunctuation() {
        List<NegotiationItem> items =
                List.of(new NegotiationItem("节能区域信息", "松山湖"), new NegotiationItem("节能速率保障目标", "20Mbps"));

        assertEquals("1. 节能区域信息：松山湖\n2. 节能速率保障目标：20Mbps", formatter.format(items, "："));
        assertEquals(
                "1. Energy-saving area information: Songshan Lake\n2. Rate guarantee target: 20 Mbps",
                formatter.format(
                        List.of(
                                new NegotiationItem("Energy-saving area information", "Songshan Lake"),
                                new NegotiationItem("Rate guarantee target", "20 Mbps")),
                        ": "));
    }

    @Test
    void omitsColonAndValueWhenValueIsNullOrBlank() {
        List<NegotiationItem> items = List.of(new NegotiationItem("名称", null), new NegotiationItem("另一个名称", "  "));

        assertEquals("1. 名称\n2. 另一个名称", formatter.format(items, "："));
    }

    @Test
    void rendersEmptyStringForNullOrEmptyList() {
        assertEquals("", formatter.format(null, "："));
        assertEquals("", formatter.format(List.of(), "："));
    }

    @Test
    void toleratesNullColonPunctuation() {
        assertEquals("1. 名称值", formatter.format(List.of(new NegotiationItem("名称", "值")), null));
    }
}
