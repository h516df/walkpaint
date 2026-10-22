import java.nio.file.*;
import java.security.*;
import java.util.*;
class KeyProbe {
 public static void main(String[] args) throws Exception {
  char[] sp=System.getenv("PROBE_STORE_PASSWORD").toCharArray();
  char[] kp=System.getenv("PROBE_KEY_PASSWORD").toCharArray();
  try {
   KeyStore ks=KeyStore.getInstance(Path.of(args[0]).toFile(),sp);
   String alias=args[1];
   PrivateKey key=(PrivateKey)ks.getKey(alias,kp);
   if(key==null)throw new IllegalStateException();
   var cert=ks.getCertificate(alias);
   String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(cert.getEncoded()));
   Signature s=Signature.getInstance(key.getAlgorithm().equals("EC")?"SHA256withECDSA":"SHA256withRSA");
   byte[] challenge=new byte[64];new SecureRandom().nextBytes(challenge);
   s.initSign(key);s.update(challenge);byte[] signed=s.sign();
   s.initVerify(cert);s.update(challenge);if(!s.verify(signed))throw new IllegalStateException();
   System.out.println("{\"certificateSha256\":\""+hash+"\",\"privateKeyVerified\":true}");
  } catch(Exception e) {System.err.println("Original private-key verification failed.");System.exit(1);}
  finally {Arrays.fill(sp,'\0');Arrays.fill(kp,'\0');}
 }
}
