package com.miniscreen.takeover;

import android.content.Context;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/** Token-scoped loopback origin supplies COOP/COEP for QEMU workers. No arbitrary filesystem API. */
final class PebbleServer implements AutoCloseable {
    interface Events {void status(String text);void frame(byte[] png);void config(String url);}
    private final Context context;private final Events events;private final ServerSocket server;
    private final ExecutorService pool=Executors.newFixedThreadPool(4);private final String token=UUID.randomUUID().toString();
    private final ConcurrentLinkedQueue<JSONObject> commands=new ConcurrentLinkedQueue<>();
    volatile boolean network;private volatile boolean closed;
    PebbleServer(Context c,Events e)throws IOException{context=c;events=e;int preferred=49173;ServerSocket candidate;
        candidate=new ServerSocket();candidate.setReuseAddress(true);try{candidate.bind(new InetSocketAddress("127.0.0.1",preferred),8);}catch(IOException ex){candidate.close();candidate=new ServerSocket(0,8,InetAddress.getByName("127.0.0.1"));}server=candidate;
        Thread accept=new Thread(()->{while(!closed)try{Socket socket=server.accept();pool.execute(()->handle(socket));}catch(IOException|RejectedExecutionException error){if(!closed)events.status("server_error");}},"Pebble HTTP");accept.setDaemon(true);accept.start();}
    String origin(){return "http://127.0.0.1:"+server.getLocalPort();}
    String base(){return origin()+"/"+token+"/";}
    void command(JSONObject value){if(commands.size()<100)commands.add(value);}
    private static String line(InputStream in)throws IOException{ByteArrayOutputStream b=new ByteArrayOutputStream();int n;while((n=in.read())!=-1&&n!='\n'){if(b.size()>8192)throw new IOException("Header too long");if(n!='\r')b.write(n);}return b.toString("US-ASCII");}
    private void handle(Socket socket){try(Socket s=socket){s.setSoTimeout(15000);InputStream in=new BufferedInputStream(s.getInputStream());String request=line(in);String[] first=request.split(" ");if(first.length!=3)return;Map<String,String> headers=new HashMap<>();for(int i=0;i<60;i++){String h=line(in);if(h.isEmpty())break;int split=h.indexOf(':');if(split>0)headers.put(h.substring(0,split).trim().toLowerCase(Locale.ROOT),h.substring(split+1).trim());}
        URI uri=URI.create(first[1]);String path=uri.getPath();if(!path.startsWith("/"+token+"/")){reply(s,403,"text/plain",new byte[0]);return;}
        path=path.substring(token.length()+2);long length=Long.parseLong(headers.getOrDefault("content-length","0"));long max=path.startsWith("state/")?40L*1024*1024:2L*1024*1024;if(length<0||length>max){reply(s,413,"text/plain",new byte[0]);return;}byte[] body=new byte[(int)length];int offset=0;while(offset<body.length){int n=in.read(body,offset,body.length-offset);if(n<0)throw new IOException("Incomplete request");offset+=n;}
        if(path.equals("commands")){JSONArray array=new JSONArray();JSONObject cmd;while((cmd=commands.poll())!=null)array.put(cmd);reply(s,200,"application/json",array.toString().getBytes(StandardCharsets.UTF_8));return;}
        if(path.equals("frame")&&first[0].equals("POST")){if(body.length<512*1024)events.frame(body);reply(s,200,"text/plain",new byte[0]);return;}
        if(path.equals("status")&&first[0].equals("POST")){if(body.length<4096)events.status(new String(body,StandardCharsets.UTF_8));reply(s,200,"text/plain",new byte[0]);return;}
        if(path.equals("config")&&first[0].equals("POST")){if(body.length<1024*1024)events.config(new String(body,StandardCharsets.UTF_8));reply(s,200,"text/plain",new byte[0]);return;}
        if(path.equals("network")){if(!network){reply(s,403,"text/plain",new byte[0]);return;}proxy(s,uri,first[0],headers,body);return;}
        if(path.startsWith("pkjs/")){String id=path.substring(5);if(!id.matches("[0-9a-f]{32}")){reply(s,404,"text/plain",new byte[0]);return;}File stored=new File(PebbleFiles.root(context),"pkjs-"+id+".json");if(first[0].equals("POST")){if(body.length>1024*1024)throw new IOException("Storage limit");new JSONObject(new String(body,StandardCharsets.UTF_8));File tmp=new File(stored.getPath()+".tmp");try(OutputStream out=new FileOutputStream(tmp)){out.write(body);}java.nio.file.Files.move(tmp.toPath(),stored.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);reply(s,200,"application/json",new byte[0]);return;}if(stored.exists())file(s,stored);else reply(s,200,"application/json","{}".getBytes(StandardCharsets.UTF_8));return;}
        if(path.startsWith("state/")){String board=path.substring(6);if(!Arrays.asList(PebbleFiles.PLATFORMS).contains(board)){reply(s,404,"text/plain",new byte[0]);return;}File state=new File(PebbleFiles.root(context),"state-"+PebbleFiles.stateKey(context,board)+".bin");if(first[0].equals("POST")){if(body.length<1024*1024)throw new IOException("Invalid flash snapshot");File tmp=new File(state.getPath()+".tmp");try(OutputStream out=new FileOutputStream(tmp)){out.write(body);}java.nio.file.Files.move(tmp.toPath(),state.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);reply(s,200,"text/plain",new byte[0]);return;}file(s,state);return;}
        if(!first[0].equals("GET")&&!first[0].equals("HEAD")){reply(s,405,"text/plain",new byte[0]);return;}
        if(path.equals("watchface.pbw")){file(s,PebbleFiles.watchface(context));return;}
        if(path.startsWith("runtime/")){String[] parts=path.substring(8).split("/",-1);if(parts.length!=2){reply(s,404,"text/plain",new byte[0]);return;}
            try(InputStream asset=PebbleFiles.openRuntime(context,parts[0],parts[1])){ByteArrayOutputStream bytes=new ByteArrayOutputStream();PebbleFiles.copy(asset,bytes,128L*1024*1024);reply(s,200,mime(parts[1]),bytes.toByteArray());}catch(FileNotFoundException notFound){reply(s,404,"text/plain",new byte[0]);}return;}
        if(path.contains("..")||path.startsWith("/")||path.contains("\\")){reply(s,403,"text/plain",new byte[0]);return;}
        try(InputStream asset=context.getAssets().open("pebble/"+path)){ByteArrayOutputStream b=new ByteArrayOutputStream();PebbleFiles.copy(asset,b,8*1024*1024);reply(s,200,mime(path),b.toByteArray());}catch(IOException notFound){reply(s,404,"text/plain",new byte[0]);}
    }catch(Exception ignored){/* A malformed/local client cannot terminate the accept loop. */}}
    private static String mime(String path){return path.endsWith(".js")?"text/javascript":path.endsWith(".html")?"text/html":path.endsWith(".json")?"application/json":path.endsWith(".wasm")?"application/wasm":"application/octet-stream";}
    private static void reply(Socket socket,int code,String type,byte[] bytes)throws IOException{OutputStream out=socket.getOutputStream();out.write(("HTTP/1.1 "+code+" Response\r\nContent-Type: "+type+"\r\nContent-Length: "+bytes.length+"\r\nConnection: close\r\nCache-Control: no-store\r\nDocument-Isolation-Policy: isolate-and-require-corp\r\nCross-Origin-Opener-Policy: same-origin\r\nCross-Origin-Embedder-Policy: require-corp\r\nCross-Origin-Resource-Policy: same-origin\r\nContent-Security-Policy: default-src 'none'; script-src 'self' 'unsafe-inline' 'unsafe-eval' 'wasm-unsafe-eval' blob:; worker-src 'self' blob:; connect-src 'self'; frame-src 'self' data: blob:; style-src 'self' 'unsafe-inline'; img-src 'self' data:; base-uri 'self'; form-action 'none'\r\nX-Content-Type-Options: nosniff\r\n\r\n").getBytes(StandardCharsets.US_ASCII));out.write(bytes);out.flush();}
    private static void file(Socket s,File f)throws IOException{if(!f.isFile()){reply(s,404,"text/plain",new byte[0]);return;}OutputStream out=s.getOutputStream();out.write(("HTTP/1.1 200 OK\r\nContent-Type: "+mime(f.getName())+"\r\nContent-Length: "+f.length()+"\r\nConnection: close\r\nDocument-Isolation-Policy: isolate-and-require-corp\r\nCross-Origin-Opener-Policy: same-origin\r\nCross-Origin-Embedder-Policy: require-corp\r\nCross-Origin-Resource-Policy: same-origin\r\nContent-Security-Policy: default-src 'none'; script-src 'self' 'unsafe-inline' 'unsafe-eval' 'wasm-unsafe-eval' blob:; worker-src 'self' blob:; connect-src 'self'; frame-src 'self' data: blob:; style-src 'self' 'unsafe-inline'; img-src 'self' data:; base-uri 'self'; form-action 'none'\r\nCache-Control: no-store\r\n\r\n").getBytes(StandardCharsets.US_ASCII));try(InputStream in=new FileInputStream(f)){PebbleFiles.copy(in,out,128L*1024*1024);}out.flush();}
    private static boolean privateHost(InetAddress a){return a.isAnyLocalAddress()||a.isLoopbackAddress()||a.isLinkLocalAddress()||a.isSiteLocalAddress()||a.isMulticastAddress()||(a instanceof Inet6Address&&(a.getAddress()[0]&0xfe)==0xfc);}
    private void proxy(Socket socket,URI request,String method,Map<String,String> headers,byte[] body)throws Exception{
        String query=request.getRawQuery();if(query==null||!query.startsWith("url="))throw new IOException("Missing URL");URI target=URI.create(URLDecoder.decode(query.substring(4),"UTF-8"));
        // External HTTP never has access to local services or the emulator's own origin.
        if(!Arrays.asList("https","http").contains(target.getScheme())||target.getUserInfo()!=null||target.getHost()==null)throw new IOException("Invalid network URL");
        for(InetAddress address:InetAddress.getAllByName(target.getHost()))if(privateHost(address))throw new IOException("Private network blocked");
        HttpURLConnection connection=(HttpURLConnection)target.toURL().openConnection();connection.setInstanceFollowRedirects(false);connection.setConnectTimeout(12000);connection.setReadTimeout(12000);connection.setRequestMethod(method);
        String contentType=headers.get("content-type");if(contentType!=null)connection.setRequestProperty("Content-Type",contentType);if(body.length>0){connection.setDoOutput(true);try(OutputStream out=connection.getOutputStream()){out.write(body);}}
        try{int code=connection.getResponseCode();ByteArrayOutputStream data=new ByteArrayOutputStream();InputStream response=code>=400?connection.getErrorStream():connection.getInputStream();if(response!=null)try(InputStream in=response){PebbleFiles.copy(in,data,8*1024*1024);}reply(socket,code,connection.getContentType()==null?"application/octet-stream":connection.getContentType(),data.toByteArray());}finally{connection.disconnect();}
    }
    @Override public void close(){closed=true;try{server.close();}catch(IOException ignored){}pool.shutdownNow();}
}
