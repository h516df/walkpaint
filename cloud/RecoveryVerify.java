import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.attribute.PosixFilePermissions;
import java.io.*;
import java.security.*;
import javax.crypto.*;
import javax.crypto.spec.*;
import java.util.*;
import java.util.regex.*;
import java.util.zip.*;

/** Portable AES-256-GCM restore; no Windows, DPAPI or network dependency. */
class RecoveryVerify {
    static final byte[] MAGIC = "H516AR01".getBytes(StandardCharsets.US_ASCII);
    static final byte[] AAD = "h516df-android-recovery-v1".getBytes(StandardCharsets.US_ASCII);
    static String hash(byte[] value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    }
    static String field(String json, String name) {
        var match=Pattern.compile("\""+name+"\"\\s*:\\s*\"([^\"]+)\"").matcher(json);
        if (!match.find()) throw new IllegalArgumentException();
        return match.group(1);
    }
    static void verify(Path archive, Path identityPath) throws Exception {
        byte[] key=Base64.getDecoder().decode(System.getenv("RECOVERY_KEY_BASE64"));
        if (key.length!=32) throw new IllegalArgumentException();
        byte[] input=Files.readAllBytes(archive);
        if(input.length<36 || input.length>1024*1024 || !Arrays.equals(Arrays.copyOfRange(input,0,8),MAGIC)) throw new IllegalArgumentException();
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,Arrays.copyOfRange(input,8,20)));
        cipher.updateAAD(AAD);
        byte[] plain=cipher.doFinal(Arrays.copyOfRange(input,20,input.length));
        Arrays.fill(key,(byte)0);
        Map<String,byte[]> entries=new HashMap<>();
        try(var zip=new ZipInputStream(new ByteArrayInputStream(plain))) {
            ZipEntry entry;
            while((entry=zip.getNextEntry())!=null) {
                if(!Set.of("original.keystore","signing.properties").contains(entry.getName()) || entries.containsKey(entry.getName())) throw new IllegalArgumentException();
                byte[] data=zip.readNBytes(1024*1024+1);
                if(data.length>1024*1024) throw new IllegalArgumentException();
                entries.put(entry.getName(),data);
            }
        }
        if(entries.size()!=2) throw new IllegalArgumentException();
        Properties props=new Properties();
        props.load(new ByteArrayInputStream(entries.get("signing.properties")));
        String expected=Files.readString(identityPath);
        for(String name:List.of("certificateSha256","packageName","alias","repository")) {
            if(!field(expected,name).equals(props.getProperty(name))) throw new IllegalArgumentException();
        }
        byte[] material=entries.get("original.keystore");
        if(!hash(material).equals(props.getProperty("keystoreSha256"))) throw new IllegalArgumentException();
        char[] sp=props.getProperty("storePassword").toCharArray(), kp=props.getProperty("keyPassword").toCharArray();
        // Java can detect both JKS and PKCS12; do not create a replacement key.
        KeyStore ks;
        try {ks=KeyStore.getInstance("PKCS12");ks.load(new ByteArrayInputStream(material),sp);}
        catch(Exception first) {ks=KeyStore.getInstance("JKS");ks.load(new ByteArrayInputStream(material),sp);}
        String alias=props.getProperty("alias");
        PrivateKey original=(PrivateKey)ks.getKey(alias,kp);
        var cert=ks.getCertificate(alias);
        if(original==null || !hash(cert.getEncoded()).equals(props.getProperty("certificateSha256"))) throw new IllegalArgumentException();
        byte[] challenge=new byte[64];new SecureRandom().nextBytes(challenge);
        Signature sig=Signature.getInstance(original.getAlgorithm().equals("EC")?"SHA256withECDSA":"SHA256withRSA");
        sig.initSign(original);sig.update(challenge);byte[] signed=sig.sign();
        sig.initVerify(cert);sig.update(challenge);if(!sig.verify(signed)) throw new IllegalArgumentException();
        Arrays.fill(sp,'\0');Arrays.fill(kp,'\0');Arrays.fill(plain,(byte)0);
        System.out.println("{\"archiveSha256\":\""+hash(input)+"\",\"certificateSha256\":\""+hash(cert.getEncoded())+"\",\"restoredOriginalBytes\":true,\"privateKeyVerified\":true,\"windowsProfileRequired\":false}");
    }
    public static void main(String[] args) {
        try {verify(Path.of(args[0]),Path.of(args[1]));}
        catch(Exception error) {System.err.println("Portable recovery verification failed; sensitive diagnostics withheld.");System.exit(1);}
    }
}
