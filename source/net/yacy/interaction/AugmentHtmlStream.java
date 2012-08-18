package net.yacy.interaction;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.net.URLEncoder;

import net.yacy.yacy;
import net.yacy.cora.document.ASCII;
import net.yacy.cora.protocol.Domains;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.cora.protocol.http.HTTPClient;
import net.yacy.document.Document;
import net.yacy.document.TextParser;
import net.yacy.kelondro.data.meta.DigestURI;
import net.yacy.kelondro.logging.Log;
import net.yacy.search.Switchboard;

import org.jsoup.Jsoup;

import de.anomic.http.server.ServerSideIncludes;


public class AugmentHtmlStream {

    static RequestHeader globalrequestHeader;

    /**
     * send web page to external REFLECT web service
     *
     * @return the web page with integrated REFLECT elements
     */
    private static String processExternal(String url, String fieldname, String data) throws IOException {
        final HTTPClient client = new HTTPClient();
        try {
            StringBuilder postdata = new StringBuilder();
            postdata.append(fieldname);
            postdata.append('=');
            postdata.append(URLEncoder.encode(data, "UTF-8"));
            InputStream in = new ByteArrayInputStream(postdata.toString()
                    .getBytes());
            byte[] result = client.POSTbytes(url, in, postdata.length());
            if (result != null) {
                return new String(result);
            }
        } finally {
            client.finish();
        }
        return null;
    }

    private static String loadInternal(String path, RequestHeader requestHeader) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        String realmProp = requestHeader.get(RequestHeader.AUTHORIZATION);
        ServerSideIncludes.writeContent(path, buffer, realmProp, Domains.LOCALHOST, requestHeader); // TODO: ip
        return buffer.toString();
    }

    /**
     * add DOCTYPE if necessary
     *
     * @return the web page with a leading DOCTYPE definition
     */
    private static String processAddDoctype(String data) {

        String result = data;

        BufferedReader reader = new BufferedReader(new StringReader(data));

        try {
            String firstline = reader.readLine();

            if (firstline != null) {
                if (!firstline.startsWith("<!DOCTYPE")) {
                    result = "<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.01 Transitional//EN\" \"http://www.w3.org/TR/html4/loose.dtd\">\n"
                            + data;
                }
            }
        } catch (IOException e1) {

        }

        return result;

    }

    /**
     * load snippet from resource text file
     *
     * @return text from resource text file
     */
    private static String loadPart(String part) {
        String result = "";
    try {
        BufferedReader in = new BufferedReader(new FileReader(yacy.homedir + File.separatorChar + "htroot"
                + File.separatorChar + "interaction" + File.separatorChar
                + "parts" + File.separatorChar + part));
        String str;
        while ((str = in.readLine()) != null) {
            result += str;
        }
        in.close();
    } catch (IOException e) {
    }

    return result;
    }

    public static StringBuffer process(StringBuffer data, DigestURI url, RequestHeader requestHeader) {

        String action =  requestHeader.get("YACYACTION");
        requestHeader.remove("YACYACTION");

        globalrequestHeader = requestHeader;

        Switchboard sb = Switchboard.getSwitchboard();

        boolean augmented = false;

        try {
            Log.logInfo("AUGMENTATION", url.getName());
        } catch (IOException e1) {
            // TODO Auto-generated catch block
            e1.printStackTrace();
        }

        String Doc = data.toString();

        // Send document to REFLECT (http://www.reflect.ws/REST_API.html)
        if (sb.getConfigBool("augmentation.reflect", false) == true) {
            try {

                Doc = processExternal("http://reflect.ws/REST/GetHTML", "document", Doc);
                Log.logInfo("AUGMENTATION", "reflected " + url);
                augmented = true;
            } catch (Exception e) {

            }
        }

        // Add DOCTYPE if not present.
        // This is required for IE to render position:absolute correctly.

        if (sb.getConfigBool("augmentation.addDoctype", false) == true) {
            Doc = processAddDoctype(Doc);
            augmented = true;
        }
      
        
        if (sb.getConfigBool("augmentation.reparse", false) == true) {
        	
        	org.jsoup.nodes.Document d = Jsoup.parse(Doc);
        	
        	d.title ("yacy - "+d.title());
        	
        	if (sb.getConfigBool("interaction.overlayinteraction.enabled", false) == true) {
        	
        		d.head().append (loadInternal("env/templates/jqueryheader.template", requestHeader));
	        	d.head().append ("<script type='text/javascript'>"+loadInternal("interaction_elements/interaction.js", requestHeader)+"</script>");
	        	d.head().append ("<script type='text/javascript'>"+loadInternal("interaction_elements/interaction_metadata.js", requestHeader)+"</script>");        	
        	
        	                  
	        	d.body().append (loadInternal("interaction_elements/OverlayInteraction.html?action="+action+"&urlhash="+ ASCII.String(url.hash()) +"&url="+url.toNormalform(false, true), requestHeader));
	        	d.body().append (loadInternal("interaction_elements/Footer.html?action="+action+"&urlhash="+ ASCII.String(url.hash()) +"&url="+url.toNormalform(false, true), requestHeader));            
        	
        	}
        	
        	Doc = d.html();
        	
        	augmented = true;
        }


        if (augmented) {
            return (new StringBuffer (Doc));
        }
        return (data);
    }

}