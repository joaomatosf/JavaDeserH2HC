import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import sun.misc.BASE64Decoder;

import java.io.*;
import java.lang.annotation.IncompleteAnnotationException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
//this import is only for java 1.8
//import java.util.Base64;
import java.util.zip.GZIPInputStream;

/**
 * Simples Servidor HTTP que desserializa dados recebidos nos seguintes formatos:
 *
 * 1) via HTTP POST em formato binário (ou seja, \xAC\xED)
 * 2) via HTTP POST como valor de algum parâmetro (eg. "ViewState") nos formatos 1) base64 (rO0...) ou 2) gzip+base64 (H4sI...)
 * 3) via cookies (header cookie) nos formatos base64 (rO0) ou gzip+base64 (H4sI) (eg. Cookie: JSESSIONID=rO0... ou Cookie: JSESSIONID=H4sI...)
 *
 * Após a desserialização, ele tenta fazer um cast para Integer, a fim de simular o que
 * ocorre em um servidor "real" (erro de casting após a desserialização)
 *
 * -----------------------------------------------------------------------
 * Mais detalhes na 14a edição da H2HC (hackers to hackers) magazine:
 * https://www.h2hc.com.br/revista/
 * -----------------------------------------------------------------------
 *
 * **** Uso ****
 *
 * Compilando:
 * $ javac VulnerableHTTPServer.java -XDignore.symbol.file
 *
 * Executando
 * $ java VulnerableHTTPServer
 *
 * Ou, caso deseje testar payloads para explorar gadgets de bibliotecas específicas, use o -cp. Ex:
 * $ java -cp .:commons-collections-3.2.1.jar VulnerableHTTPServer
 *
 * @author @joaomatosf
 */

public class VulnerableHTTPServer {

    public static void banner(){
        System.out.println("* =============================================================== *");
        System.out.println("*    Simple Java HTTP Server for Deserialization Lab v0.01        *");
        System.out.println("*    https://github.com/joaomatosf/JavaDeserH2HC                  *");
        System.out.println("* =============================================================== *");
        System.out.println("You can inject java serialized objects in the following formats:\n");
        System.out.println(" 1) Binary in HTTP POST (ie \\xAC\\xED). Ex:\n" +
                "    $ curl 127.0.0.1:8000 --data-binary @ObjectFile.ser\n"+
                "\n 2) Base64 or Gzip+Base64 via HTTP POST parameters. Ex:\n" +
                "    $ curl 127.0.0.1:8000 -d \"ViewState=H4sICAeH...\"\n"+
                "    $ curl 127.0.0.1:8000 -d \"ViewState=rO0ABXNy...\"\n"+
                "\n 3) Base64 or Gzip+Base64 in cookies. Ex:\n"+
                "    $ curl 127.0.0.1:8000 -H \"Cookie: JSESSIONID=H4sICAeH...\"\n"+
                "    $ curl 127.0.0.1:8000 -H \"Cookie: JSESSIONID=rO0ABXNy...\"\n");

        System.out.println("OBS: To test gadgets in specific libraries, run with -cp param. Ex:\n" +
                "$ java -cp .:commons-collections-3.2.1.jar VulnerableHTTPServer");
        System.out.println("==================================================================");

    }

    public static void main(String[] args) throws IOException {
        banner();
        int port = 8000;
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", new HTTPHandler());
        server.setExecutor(null); // creates a default executor
        server.start();
        System.out.println("\nJRE Version: "+System.getProperty("java.version"));
        System.out.println("[INFO]: Listening on port "+port);
        System.out.println();
    }


    static class HTTPHandler implements HttpHandler {

        public void handle(HttpExchange t) throws IOException {

            System.out.println("[INFO]: Received "+t.getRequestMethod()+" "+t.getRequestURI()+" from: "+t.getRemoteAddress());

            String responseMsg = null;
            boolean containsCookie = t.getRequestHeaders().containsKey("cookie");

            // if there's a cookie with serialized java object
            if (containsCookie){

                String object = t.getRequestHeaders().get("cookie").get(0);
                object = object.split("=").length > 1 ? object.split("=")[1] : object.split("=")[0];
                if (object.startsWith("H4sI") || object.startsWith("rO0") )
                    responseMsg = deserialize(object);
                else
                    responseMsg = "No serialized objects found.\n";

            }
            else if (t.getRequestMethod().equals("POST")){

                InputStream input = t.getRequestBody();
                // take 2 bytes from header to check if it is a raw object
                PushbackInputStream pbis = new PushbackInputStream( input, 2 );
                byte [] header = new byte[2];
                int len = pbis.read(header);
                pbis.unread( header, 0, len );
                StringBuffer headerResult = new StringBuffer();
                for (byte b: header)
                    headerResult.append(String.format("%02x", b));

                // deserialize raw
                if (headerResult.toString().equals("aced"))
                    responseMsg = deserialize(pbis); // deserialize RAW
                else{ // deserialize H4sI, rO0
                    InputStreamReader isr = new InputStreamReader(pbis, "utf-8");
                    BufferedReader br = new BufferedReader(isr);
                    String body = br.readLine();
                    String object = body.split("=").length > 1 ? body.split("=")[1] : body.split("=")[0];
                    if (object.startsWith("H4sI") || object.startsWith("rO0") )
                        responseMsg = deserialize(object); // deserialize H4sI, rO0...
                }


            }
            else{
                responseMsg = "No serialized objects found.\n";
            }

            t.sendResponseHeaders(200, responseMsg.length());
            OutputStream os = t.getResponseBody();
            os.write(responseMsg.getBytes());
            os.close();

        }
        public String deserialize(String object){

            ObjectInputStream ois = null;
            InputStream is = null;
            GZIPInputStream gis = null;

            // if payload is urlencoded
            if (object.contains("%2B")) {
                try {
                    object = URLDecoder.decode(object, "UTF-8");
                } catch (UnsupportedEncodingException e) {
                    return "Invalid encoding. You should use URL Encode!\n";
                }
            }

            try {
                byte[] b64DecodedObj = new BASE64Decoder().decodeBuffer(object);
                // This another implementation of Base64 is only for java >= 1.8
                //byte[] b64DecodedObj = Base64.getDecoder().decode(object);
                is = new ByteArrayInputStream(b64DecodedObj);
            }catch (Exception e){
                return "Invalid Base64!\n";
            }

            if (object.startsWith("H4sI")) {
                try {
                    gis = new GZIPInputStream(is);
                    ois = new ObjectInputStream(gis);
                } catch (IOException e) {
                   return "The Stream not contains a Java Object!\n";
                }
                catch (Exception e) {
                    return "Invalid Gzip stream!\n";
                }
            }
            else {
                try {
                    ois = new ObjectInputStream(is);
                }
                catch (IOException e ){
                    return "The Stream not contains a Java Object!\n";
                }
                catch (Exception e){
                    return e.toString()+"\n";
                }
            }

            // Deserialization
            try{
                int number = (Integer) ois.readObject();
            }
            catch (ClassNotFoundException e) {
                return "Serialized class not found in classpath\n";
            }
            catch (IOException e) {
                return e.toString()+"\n";
            }
            catch (ClassCastException e){
                e.printStackTrace();
            } catch (IncompleteAnnotationException e){
                e.printStackTrace();
                System.out.println("\n[INFO] This payload not works in JRE >= 8u72. Try another version such as those\n" +
                        "       which use TiedMapEntry + HashSet (by @matthiaskaiser).\n");
                return "This payload not works in JRE >= 8u72. Try another version such as those which use TiedMapEntry + HashSet (by @matthiaskaiser).\n";
            }
            catch (Exception e){
                e.printStackTrace();
            }


            return "Data deserialized!\n";
        }

        public String deserialize(InputStream is){

            ObjectInputStream ois = null;

            try{
                ois = new ObjectInputStream(is);
            }catch (EOFException e){
                e.printStackTrace();
                return "The request body not contains a Stream!\n";
            } catch (Exception e) {
                return e.toString()+"\n";
            }

            try {
                // This cast simulate what occurs in a real server
                int number = (Integer) ois.readObject();
            } catch (IOException e) {
                e.printStackTrace();
            } catch (ClassNotFoundException e) {
                return "Serialized class not found in classpath\n";
            } catch (ClassCastException e){
                e.printStackTrace();
            } catch (IncompleteAnnotationException e){
                e.printStackTrace();
                System.out.println("\n[INFO] This payload not works in JRE >= 8u72. Try another version such as those\n" +
                        "       which use TiedMapEntry + HashSet (by @matthiaskaiser).\n");
                return "This payload not works in JRE >= 8u72. Try another version such as those which use TiedMapEntry + HashSet (by @matthiaskaiser).\n";
            }
            catch (Exception e){
                e.printStackTrace();
            }

            return "Data deserialized!\n";
        }



    }
}