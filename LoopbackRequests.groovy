import java.io.IOException;  
import java.net.URI  
import java.net.URISyntaxException  
import java.net.URL;  
import java.util.Collections;  
import java.util.HashMap;  
import java.util.Map;  
  
  
import com.vordel.circuit.CircuitAbortException  
import com.vordel.circuit.Message  
import com.vordel.circuit.MessageListenerAdapter  
import com.vordel.dwe.CachedConnection  
import com.vordel.dwe.Protocol  
import com.vordel.dwe.Service  
import com.vordel.dwe.http.ClientTransaction  
import com.vordel.dwe.http.ServerTransaction  
import com.vordel.mime.Body  
import com.vordel.mime.HeaderSet  
  
  
/** 
 * Execute a loopback call against current http listener. the property 
 * "dwe.protocol.loopback.circuitpath" will contain comma separated circuit 
 * paths. after execution. It does the same thing as 
 * 'ProtocolDelegateFilter', except it can handle any URI and it can handle 
 * an invocation error. 
 *  
 * @param msg 
 *            current handled message 
 * @return true if the call has been made (circuitpath non null), false 
 *         otherwise 
 * @throws CircuitAbortException 
 */  
boolean invoke(Message msg) throws CircuitAbortException {  
    try {  
        /* start by rewriting target URI and retrieve its path */  
        String resolved = (String) msg.get("resolved.to.path");  
        URI loopback = rewriteURL(msg);  
        String path = loopback.getPath();  
        boolean isSubPath = false;  
  
  
        if (path.equals(resolved)) {  
            /* exit if looping */  
            throw new CircuitAbortException("Can't call back myself");  
        } else {  
            /* otherwise, check for subpath */  
            isSubPath = path.startsWith(resolved);  
        }  
  
  
        /* 
         * create a thread safe view of current message and retrieve current 
         * service 
         */  
        Map<String, Object> syncMap = Collections.synchronizedMap(new HashMap<String, Object>(msg));  
        Service service = Service.getInstance();  
  
  
        /* create the client loopback transaction */  
        ClientTransaction transaction = getClientTransaction(msg, service, syncMap, isSubPath);  
  
  
        /* 
         * Retrieve request parameters which will be transmitted in the 
         * request 
         */  
        HeaderSet headers = (HeaderSet) msg.get("http.headers");  
        String verb = (String) msg.get("http.request.verb");  
        Body body = (Body) msg.get("content.body");  
  
  
        if (headers == null) {  
            /* handle empty headers */  
            headers = new HeaderSet();  
        }  
  
  
        /* send request headers */  
        transaction.sendHeaders(verb, loopback.toString(), body, headers, (HeaderSet) null, true, false, true);  
        service.inactive();  
  
  
        if (body != null) {  
            /* send request body (if it exists) XXX should match verb... */  
            transaction.sendBody(body, 4);  
        }  
  
  
        /* read transaction response */  
        transaction.readResponse().buildReply(msg);  
  
  
        /* 
         * XXX should find a better way of retrieving loopback circuit 
         * path... 
         */  
        String previousCircuitPath = (String) msg.get("dwe.protocol.loopback.circuitpath");  
        String loopbackCircuitPath = (String) syncMap.get("dwe.protocol.loopback.circuitpath");  
  
  
        if (loopbackCircuitPath != null) {  
            if (previousCircuitPath != null) {  
                /* 
                 * keep track of previous loopback circuitpath in case of 
                 * multiple calls 
                 */  
                loopbackCircuitPath = previousCircuitPath + "," + loopbackCircuitPath;  
            }  
  
  
            /* save loopback circuit path in current message */  
            msg.put("dwe.protocol.loopback.circuitpath", loopbackCircuitPath);  
        }  
  
  
        /* no circuit path means that loopback call failed */  
        return loopbackCircuitPath != null;  
    } catch (IOException e) {  
        throw new CircuitAbortException("IO error communicating with remote", e);  
    }  
}  
  
  
/** 
 * create a loopback client transaction, taking care of subpath cases. 
 *  
 * @param msg 
 *            current message whiteboard 
 * @param service 
 *            current service 
 * @param syncMap 
 *            thread safe view of current message whiteboard 
 * @param isSubPath 
 *            true if in the context of a subpath, false otherwise. 
 * @return 
 */  
ClientTransaction getClientTransaction(Message msg, Service service, Map<String, Object> syncMap, boolean isSubPath) {  
    ServerTransaction txn = (ServerTransaction) msg.get("http.client");  
    Protocol protocol = null;  
  
  
    if (isSubPath) {  
        /* We are in the context of a subpath, takes the child handler */  
        protocol = (Protocol) msg.get("dwe.protocol.loopback");  
    } else {  
        /* otherwise, take the parent handler */  
        protocol = (Protocol) msg.get("dwe.protocol");  
    }  
  
  
    /* create a loopback connection and a client transaction */  
    final CachedConnection serverConnection = protocol.getLoopbackConnection(service, txn, syncMap);  
    final ClientTransaction transaction = new ClientTransaction(serverConnection, msg.correlationId);  
  
  
    /* 
     * XXX don't know usage of this property (present in filter 'Call 
     * Internal Service') 
     */  
    msg.put("dwe.pushresponse", Boolean.valueOf(true));  
  
  
    msg.addMessageListener(new MessageListenerAdapter() {  
                public void onMessageCompletion(Message m) {  
                    transaction.dispose();  
                    serverConnection.dispose();  
                }  
            });  
  
  
    return transaction;  
}  
  
  
/** 
 * Takes the "http.request.uri" , normalize it and remove store query and 
 * fragment. returns normalized URI with query and fragment. 
 *  
 * @param msg 
 *            current message whiteboard 
 * @return the updated normalized URI 
 * @throws CircuitAbortException 
 */  
URI rewriteURL(Message msg) throws CircuitAbortException {  
    try {  
        URI loopback = new URI(msg.get("http.request.uri").toString());  
  
  
        /* extract URI parts */  
        String path = loopback.getPath();  
        String query = loopback.getQuery();  
        String fragment = loopback.getFragment();  
  
  
        /* rebuild and normalize loopback URI */  
        loopback = new URI(null, null, path, query, fragment).normalize();  
  
  
        /* extract normalized path, fragment and query */  
        path = loopback.getPath();  
        query = loopback.getQuery();  
        fragment = loopback.getFragment();  
  
  
        /* write only URI path to property */  
        msg.put("http.request.uri", new URI(null, null, path, null, null));  
  
  
        /* and return path, query and fragment */  
        return new URI(null, null, path, query, fragment);  
    } catch (URISyntaxException e) {  
        throw new CircuitAbortException("Error retrieving target transaction URI", e);  
    }  
}  
