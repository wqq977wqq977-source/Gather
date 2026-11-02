package com.qianwang.privatewealth;

/** Checks public release metadata only; installation remains a user action. */
public final class UpdateClient {
    public static final String REPOSITORY="https://github.com/wqq977wqq977-source/Gather";
    public static String latest() throws Exception {
        org.json.JSONObject release=new org.json.JSONObject(QuoteClient.read("https://api.github.com/repos/wqq977wqq977-source/Gather/releases/latest"));
        if(release.optBoolean("draft")||release.optBoolean("prerelease"))throw new java.io.IOException("Not a stable release");
        String tag=release.getString("tag_name");parts(tag);return tag;
    }
    private static int[] parts(String version){
        if(!version.matches("v?[0-9]+\\.[0-9]+\\.[0-9]+"))throw new IllegalArgumentException("Invalid version");
        String[] values=version.replaceFirst("^v","").split("\\.");return new int[]{Integer.parseInt(values[0]),Integer.parseInt(values[1]),Integer.parseInt(values[2])};
    }
    public static boolean isNewer(String remote,String local){int[] a=parts(remote),b=parts(local);for(int i=0;i<3;i++)if(a[i]!=b[i])return a[i]>b[i];return false;}
}
