package com.qianwang.privatewealth;

import java.util.*;
import java.math.*;

/** Every internal node conserves its children; all raw values remain CNY fen. */
public final class AssetTree {
    public static final class Node {
        public final String key,name,accountId,holdingId;
        public final long value;
        public final List<Node> children;
        public Node(String key,String name,String accountId,String holdingId,long value,List<Node> children){
            this.key=key;this.name=name;this.accountId=accountId;this.holdingId=holdingId;
            List<Node> sorted=new ArrayList<>(children);Collections.sort(sorted,(a,b)->Long.compare(b.value,a.value));this.children=Collections.unmodifiableList(sorted);
            if(value<0)throw new IllegalArgumentException("资产金额无效");long sum=0;for(Node n:sorted)sum=Math.addExact(sum,n.value);if(!sorted.isEmpty()&&sum!=value)throw new IllegalArgumentException("分支金额不守恒");this.value=value;
        }
    }
    private static Node leaf(String key,String name,String account,String holding,long value){return new Node(key,name,account,holding,value,Collections.emptyList());}
    private static Node parent(String key,String name,String account,List<Node> children){long total=0;for(Node n:children)total=Math.addExact(total,n.value);return new Node(key,name,account,null,total,children);}
    public static Node build(List<WealthStore.Account> accounts,List<WealthStore.Holding> holdings,boolean byAccount){
        List<Node> roots=new ArrayList<>();
        for(int k=0;k<(byAccount?1:AssetSankey.CATEGORY_NAMES.length);k++){
            List<Node> category=new ArrayList<>();
            for(WealthStore.Account a:accounts){
                List<Node> parts=new ArrayList<>();int cashCategory="bank".equals(a.kind)?0:"securities".equals(a.kind)?2:3;
                if(a.cashFen>0&&(byAccount||cashCategory==k))parts.add(leaf("cash:"+a.id,"可用现金",a.id,null,a.cashFen));
                for(WealthStore.Holding h:holdings)if(h.accountId.equals(a.id)&&(byAccount||(WealthStore.isCrypto(h)?4:1)==k)){long value=WealthStore.holdingValue(h);if(value>0)parts.add(leaf("holding:"+h.id,h.name,a.id,h.id,value));}
                if(!parts.isEmpty())category.add(parent("account:"+a.id+":"+k,a.name,a.id,parts));
            }
            if(byAccount)roots.addAll(category);else if(!category.isEmpty())roots.add(parent("category:"+k,AssetSankey.CATEGORY_NAMES[k],null,category));
        }
        // Orphans must never disappear from the displayed total.
        for(WealthStore.Holding h:holdings){boolean found=false;for(WealthStore.Account a:accounts)if(a.id.equals(h.accountId))found=true;if(!found)throw new IllegalArgumentException("持仓所属账户不存在");}
        return parent("root","总资产",null,roots);
    }
    public static int categoryColor(String key){return key.matches("category:[0-4]")?Integer.parseInt(key.substring(9)):-1;}
    public static List<Node> visible(Node node){if(node.children.size()<=6)return node.children;List<Node> result=new ArrayList<>(node.children.subList(0,5));result.add(parent(node.key+"/rest","其余 "+(node.children.size()-5)+" 项",null,node.children.subList(5,node.children.size())));return result;}
    public static String share(long part,long total){if(total==0)return "—";BigDecimal pct=BigDecimal.valueOf(part).multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(total),1,RoundingMode.HALF_UP);return part>0&&pct.signum()==0?"<0.1%":pct.toPlainString()+"%";}
}
