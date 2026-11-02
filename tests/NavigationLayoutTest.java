package com.qianwang.privatewealth;
import java.util.Arrays;
public final class NavigationLayoutTest {
    private static int checks;
    public static void main(String[] args){
        eq("4,0,3,1",NavigationLayout.encode(NavigationLayout.parse("")),"empty defaults");
        for(String malformed:new String[]{null,"4,4,3,1","2,0,3,1","4,0,3","four,0,3,1","4,0,3,1,2"})eq("4,0,3,1",NavigationLayout.encode(NavigationLayout.parse(malformed)),"invalid order safely defaults");
        int[] order=NavigationLayout.defaults();int[] moved=NavigationLayout.move(order,1,-1);
        eq("0,4,3,1",NavigationLayout.encode(moved),"adjacent move");eq("4,0,3,1",NavigationLayout.encode(order),"move does not mutate input");
        eq("4,0,3,1",NavigationLayout.encode(NavigationLayout.move(order,0,-1)),"first boundary");eq("4,0,3,1",NavigationLayout.encode(NavigationLayout.move(order,3,1)),"last boundary");
        eq("1,3,0,4",NavigationLayout.encode(NavigationLayout.parse("1,3,0,4")),"custom order round trips");
        eq("calendar",NavigationLayout.icon(4,"unknown"),"invalid icon falls back");eq("star",NavigationLayout.icon(4,"star"),"custom icon kept");eq("hold",NavigationLayout.defaultIcon(1),"holding default");
        for(int page:order){truth(!NavigationLayout.label(page).isEmpty(),"page label");truth(Arrays.asList(NavigationLayout.ICON_IDS).contains(NavigationLayout.defaultIcon(page)),"defaults are choices");}
        truth(NavigationLayout.ICON_IDS.length==NavigationLayout.ICON_NAMES.length,"icon names align");
        truth(new java.util.HashSet<>(Arrays.asList(NavigationLayout.ICON_IDS)).size()==NavigationLayout.ICON_IDS.length,"unique choices");
        System.out.println("PASS "+checks+" navigation assertions");
    }
    private static void eq(String expected,String actual,String label){truth(expected.equals(actual),label);}
    private static void truth(boolean test,String label){checks++;if(!test)throw new AssertionError(label);}
}
