package trustcheck;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.Executors;

/** Small, dependency-free HTTP server for TrustCheck. */
public final class TrustCheckServer {
    private final Path root;
    private final EntityRepository entities;
    private final ReportRepository reports;
    private final VerificationService verifier;

    private TrustCheckServer(Path root) throws IOException {
        this.root = root.toAbsolutePath().normalize();
        Path data = this.root.resolve("data");
        Files.createDirectories(data);
        entities = new EntityRepository(this.root.resolve("data/verified-entities.csv"));
        reports = new ReportRepository(data.resolve("reports.db"));
        verifier = new VerificationService(entities, reports, new KeywordDetector());
    }

    public static void main(String[] args) throws Exception {
        Path root = Path.of(args.length > 0 ? args[0] : ".").toAbsolutePath().normalize();
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
        TrustCheckServer app = new TrustCheckServer(root);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        server.createContext("/api/verify", app::verify);
        server.createContext("/api/reports", app::reportApi);
        server.createContext("/api/stats", app::stats);
        server.createContext("/", app::staticFiles);
        server.setExecutor(Executors.newFixedThreadPool(8));
        server.start();
        System.out.println("TrustCheck is running at http://localhost:" + port);
    }

    private void verify(HttpExchange x) throws IOException {
        if (!method(x, "POST")) return;
        Map<String,String> form = readForm(x);
        String query = clean(form.get("query"), 180), category = clean(form.get("category"), 30);
        if (query.isBlank() || !Set.of("company", "internship", "certificate").contains(category)) {
            send(x, 400, "application/json; charset=utf-8", "{\"error\":\"Enter a name and choose a verification category.\"}"); return;
        }
        String context = clean(form.get("details"), 1200);
        send(x, 200, "application/json; charset=utf-8", verifier.verify(query, category, context).toJson());
    }

    private void reportApi(HttpExchange x) throws IOException {
        if ("GET".equals(x.getRequestMethod())) {
            Map<String,String> q = query(x.getRequestURI().getRawQuery());
            send(x, 200, "application/json; charset=utf-8", reports.search(q.getOrDefault("q", ""), q.getOrDefault("category", "all")));
            return;
        }
        if (!method(x, "POST")) return;
        Map<String,String> f = readForm(x);
        String name=clean(f.get("name"),150), category=clean(f.get("category"),30), details=clean(f.get("details"),1800), contact=clean(f.get("contact"),240), evidence=clean(f.get("evidence"),500);
        if(name.isBlank() || details.length()<15 || !Set.of("internship","company","certificate").contains(category)) {
            send(x,400,"application/json; charset=utf-8","{\"error\":\"Add a name, category, and description of at least 15 characters.\"}"); return;
        }
        if(!evidence.isBlank()) { try { URI u=URI.create(evidence); if(!Set.of("http","https").contains(u.getScheme()) || u.getHost()==null) throw new IllegalArgumentException(); } catch(Exception e) { send(x,400,"application/json; charset=utf-8","{\"error\":\"Evidence must be a valid http or https URL.\"}"); return; } }
        Report r=reports.add(name,category,details,contact,evidence);
        send(x,201,"application/json; charset=utf-8",r.toJson());
    }

    private void stats(HttpExchange x) throws IOException {
        send(x,200,"application/json; charset=utf-8","{\"verifiedEntities\":"+entities.size()+",\"communityReports\":"+reports.size()+"}");
    }

    private void staticFiles(HttpExchange x) throws IOException {
        if(!"GET".equals(x.getRequestMethod())) { send(x,405,"text/plain; charset=utf-8","Method not allowed"); return; }
        String raw=x.getRequestURI().getPath();
        String rel;
        try { rel=URLDecoder.decode(raw,StandardCharsets.UTF_8); } catch(Exception e) { send(x,400,"text/plain; charset=utf-8","Bad path"); return; }
        if(rel.equals("/")) rel="/index.html";
        Path file=root.resolve("web").resolve(rel.substring(1)).normalize();
        if(!file.startsWith(root.resolve("web")) || !Files.isRegularFile(file)) { send(x,404,"text/plain; charset=utf-8","Not found"); return; }
        String type=file.toString().endsWith(".css")?"text/css":file.toString().endsWith(".js")?"text/javascript":"text/html";
        x.getResponseHeaders().set("X-Content-Type-Options","nosniff");
        byte[] bytes=Files.readAllBytes(file); x.getResponseHeaders().set("Content-Type",type+"; charset=utf-8"); x.sendResponseHeaders(200,bytes.length); try(OutputStream o=x.getResponseBody()){o.write(bytes);}
    }

    private static boolean method(HttpExchange x,String expected) throws IOException { if(expected.equals(x.getRequestMethod()))return true; send(x,405,"application/json; charset=utf-8","{\"error\":\"Method not allowed.\"}"); return false; }
    private static String clean(String s,int max){ if(s==null)return ""; String v=s.strip().replaceAll("[\\p{Cntrl}&&[^\\r\\n\\t]]",""); return v.length()>max?v.substring(0,max):v; }
    private static Map<String,String> readForm(HttpExchange x)throws IOException { return query(new String(x.getRequestBody().readAllBytes(),StandardCharsets.UTF_8)); }
    private static Map<String,String> query(String q){ Map<String,String> m=new HashMap<>(); if(q==null)return m; for(String pair:q.split("&")){String[] p=pair.split("=",2);try{m.put(URLDecoder.decode(p[0],StandardCharsets.UTF_8),p.length>1?URLDecoder.decode(p[1],StandardCharsets.UTF_8):"");}catch(Exception ignored){}} return m; }
    private static void send(HttpExchange x,int status,String type,String body)throws IOException { byte[] b=body.getBytes(StandardCharsets.UTF_8); x.getResponseHeaders().set("Content-Type",type); x.getResponseHeaders().set("Cache-Control","no-store"); x.sendResponseHeaders(status,b.length); try(OutputStream o=x.getResponseBody()){o.write(b);} }
}

final class EntityRepository {
    record Entity(String name,String category,String note){}
    private final List<Entity> entities=new ArrayList<>();
    EntityRepository(Path file)throws IOException {
        if(!Files.exists(file)) Files.writeString(file,"# Small starter dataset; this is not a directory of all legitimate entities. Edit or extend with verified references.\nOpenAI,company,Example starter record for demonstrating exact matching\nIndian Institute of Science,institution,Example starter record for demonstrating exact matching\n",StandardCharsets.UTF_8);
        for(String line:Files.readAllLines(file,StandardCharsets.UTF_8)){if(line.isBlank()||line.startsWith("#"))continue;String[] p=line.split(",",3);if(p.length==3)entities.add(new Entity(p[0].strip(),p[1].strip().toLowerCase(Locale.ROOT),p[2].strip()));}
    }
    Optional<Entity> exact(String q,String category){return entities.stream().filter(e->e.name().equalsIgnoreCase(q.strip())&&(e.category().equals(category)||category.equals("internship")&&e.category().equals("company"))).findFirst();}
    List<Entity> partial(String q){String n=q.toLowerCase(Locale.ROOT);return entities.stream().filter(e->e.name().toLowerCase(Locale.ROOT).contains(n)||n.contains(e.name().toLowerCase(Locale.ROOT))).toList();}
    int size(){return entities.size();}
}

final class KeywordDetector {
    private final List<String> phrases=List.of("guaranteed placement","pay registration fee","registration fee","pay to receive an offer letter","pay to receive offer letter","guaranteed job without an interview","guaranteed job without interview","pay for your offer letter","upfront fee","deposit to secure the internship","no interview required, guaranteed job");
    List<String> detect(String text){String s=text.toLowerCase(Locale.ROOT);return phrases.stream().filter(s::contains).distinct().toList();}
}

final class VerificationService {
    private final EntityRepository entities; private final ReportRepository reports; private final KeywordDetector keywords;
    VerificationService(EntityRepository e,ReportRepository r,KeywordDetector k){entities=e;reports=r;keywords=k;}
    Result verify(String name,String category,String context){List<String> hits=keywords.detect(name+" "+context);int allegations=reports.countFor(name);
        Optional<EntityRepository.Entity> exact=entities.exact(name,category); List<EntityRepository.Entity> partial=exact.isPresent()?List.of():entities.partial(name);
        String status, title, explanation;
        if(!hits.isEmpty()){status="SUSPICIOUS";title="Warning signs found";explanation="The text includes common scam warning phrases. These are risk signals, not proof that the sender is fraudulent.";}
        else if(exact.isPresent()&&allegations==0){status="VERIFIED";title="Exact starter-dataset match";explanation="This name exactly matches a record in the small TrustCheck starter dataset. Independently verify that the offer and sender really belong to this entity.";}
        else if(allegations>0){status="UNVERIFIED";title="Community allegations exist";explanation="Community reports mention this name. Reports are user-submitted allegations and are not independently verified.";}
        else if(exact.isPresent()){status="UNVERIFIED";title="Conflicting community reports";explanation="A starter-dataset match exists, but community allegations are present. Check official channels before proceeding.";}
        else if(!partial.isEmpty()){status="UNVERIFIED";title="Partial name match only";explanation="A similar name is in the starter dataset, but it is not an exact match and does not verify this offer.";}
        else{status="UNVERIFIED";title="No exact starter-dataset match";explanation="No matching record was found. The dataset is small and absence from it says nothing conclusive about legitimacy.";}
        return new Result(name,category,status,title,explanation,hits,exact.map(e->e.name()).orElse(""),partial.stream().map(EntityRepository.Entity::name).toList(),allegations);
    }
}
record Result(String query,String category,String status,String title,String explanation,List<String> keywords,String exactMatch,List<String> partialMatches,int reports){String toJson(){return "{\"query\":"+j(query)+",\"category\":"+j(category)+",\"status\":"+j(status)+",\"title\":"+j(title)+",\"explanation\":"+j(explanation)+",\"keywords\":"+arr(keywords)+",\"exactMatch\":"+j(exactMatch)+",\"partialMatches\":"+arr(partialMatches)+",\"reports\":"+reports+"}";} static String arr(List<String> a){return "["+String.join(",",a.stream().map(Result::j).toList())+"]";} static String j(String s){StringBuilder out=new StringBuilder("\"");for(char c:s.toCharArray()){switch(c){case '\\'->out.append("\\\\");case '\"'->out.append("\\\"");case '\n'->out.append("\\n");case '\r'->out.append("\\r");case '\t'->out.append("\\t");default->{if(c<0x20)out.append(String.format("\\u%04x",(int)c));else out.append(c);}}}return out.append('\"').toString();}}

record Report(String id,String name,String category,String details,String contact,String evidence,String date){String toJson(){return "{\"id\":"+Result.j(id)+",\"name\":"+Result.j(name)+",\"category\":"+Result.j(category)+",\"details\":"+Result.j(details)+",\"contact\":"+Result.j(contact)+",\"evidence\":"+Result.j(evidence)+",\"date\":"+Result.j(date)+",\"status\":\"Community allegation\"}";}}
final class ReportRepository {
    private final Path file; private final List<Report> reports=new ArrayList<>(); private static final DateTimeFormatter DATE=DateTimeFormatter.ofPattern("dd MMM yyyy").withZone(ZoneId.of("UTC"));
    ReportRepository(Path p)throws IOException{file=p; if(!Files.exists(file))Files.createFile(file);for(String line:Files.readAllLines(file,StandardCharsets.UTF_8)){String[] a=line.split("\\t",-1);if(a.length==7)try{reports.add(new Report(dec(a[0]),dec(a[1]),dec(a[2]),dec(a[3]),dec(a[4]),dec(a[5]),dec(a[6])));}catch(Exception ignored){}}}
    synchronized Report add(String name,String cat,String details,String contact,String evidence)throws IOException{Report r=new Report(UUID.randomUUID().toString(),name,cat,details,contact,evidence,DATE.format(Instant.now()));String line=String.join("\t",enc(r.id()),enc(r.name()),enc(r.category()),enc(r.details()),enc(r.contact()),enc(r.evidence()),enc(r.date()))+"\n";Files.writeString(file,line,StandardCharsets.UTF_8,StandardOpenOption.APPEND);reports.add(r);return r;}
    synchronized int size(){return reports.size();}
    synchronized int countFor(String name){return (int)reports.stream().filter(r->r.name().equalsIgnoreCase(name.strip())).count();}
    synchronized String search(String term,String category){String q=term.toLowerCase(Locale.ROOT);return "["+String.join(",",reports.stream().filter(r->category.equals("all")||r.category().equals(category)).filter(r->q.isBlank()||(r.name()+" "+r.details()).toLowerCase(Locale.ROOT).contains(q)).sorted(Comparator.comparing(Report::date).reversed()).map(Report::toJson).toList())+"]";}
    private static String enc(String s){return Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_8));} private static String dec(String s){return new String(Base64.getDecoder().decode(s),StandardCharsets.UTF_8);}
}
