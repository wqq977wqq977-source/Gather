package com.qianwang.privatewealth;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import javax.crypto.*;
import javax.crypto.spec.*;

/** Versioned, password-protected backup. The password and derived key are never stored. */
public final class BackupCodec {
    private static final String HEADER="GUILONG-BACKUP-1";
    private static final int ITERATIONS=210000;
    public static String encrypt(String plaintext, char[] password) throws Exception {
        if(password.length<8) throw new IllegalArgumentException("备份密码至少 8 位");
        byte[] salt=new byte[16],iv=new byte[12]; SecureRandom r=new SecureRandom(); r.nextBytes(salt);r.nextBytes(iv);
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key(password,salt),new GCMParameterSpec(128,iv));
        cipher.updateAAD(HEADER.getBytes(StandardCharsets.UTF_8));
        return HEADER+"\n"+encode(salt)+"\n"+encode(iv)+"\n"+encode(cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8)));
    }
    public static String decrypt(String data, char[] password) throws Exception {
        String[] p=data.trim().split("\n");
        if(p.length!=4 || !HEADER.equals(p[0])) throw new IllegalArgumentException("不是Gather加密备份文件");
        byte[] salt=Base64.getDecoder().decode(p[1]), iv=Base64.getDecoder().decode(p[2]);
        if(salt.length!=16 || iv.length!=12) throw new IllegalArgumentException("备份格式不完整");
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE,key(password,salt),new GCMParameterSpec(128,iv));
        cipher.updateAAD(HEADER.getBytes(StandardCharsets.UTF_8));
        return new String(cipher.doFinal(Base64.getDecoder().decode(p[3])), StandardCharsets.UTF_8);
    }
    private static SecretKey key(char[] password, byte[] salt) throws Exception {
        PBEKeySpec spec=new PBEKeySpec(password,salt,ITERATIONS,256);
        try { return new SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded(),"AES"); }
        finally {spec.clearPassword();}
    }
    private static String encode(byte[] b) {return Base64.getEncoder().encodeToString(b);}
}
