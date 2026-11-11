package nl.vdspunten.offline;

import android.graphics.BitmapFactory;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Offline WordprocessingML .docx writer. No cloud, no conversion services, no network. */
final class WordReport {
    private WordReport() {}
    static class Item {String id,description,takenAt,status;File image;String target;int relId;long cx,cy;}
    static class Group {String name;List<Item> photos=new ArrayList<>();}
    private static String xml(String s){return String.valueOf(s==null?"":s).replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&apos;");}
    private static String p(String text,String style){
        String pr=style.isEmpty()?"":"<w:pPr><w:pStyle w:val=\""+style+"\"/></w:pPr>";
        return "<w:p>"+pr+"<w:r><w:t xml:space=\"preserve\">"+xml(text)+"</w:t></w:r></w:p>";
    }
    private static String h(String text,boolean newPage){
        return "<w:p><w:pPr><w:pStyle w:val=\"Heading1\"/>"+(newPage?"<w:pageBreakBefore/>":"")+"</w:pPr><w:r><w:t>"+xml(text)+"</w:t></w:r></w:p>";
    }
    private static void entry(ZipOutputStream zip,String name,String xmlText) throws IOException{
        zip.putNextEntry(new ZipEntry(name));zip.write(xmlText.getBytes(StandardCharsets.UTF_8));zip.closeEntry();
    }
    private static void entryFile(ZipOutputStream zip,String name,File f) throws IOException{
        zip.putNextEntry(new ZipEntry(name));try(FileInputStream in=new FileInputStream(f)){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)zip.write(b,0,n);}zip.closeEntry();
    }
    private static String formatDate(String iso) {
        try {return DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm").withZone(ZoneId.systemDefault()).format(Instant.parse(iso));}
        catch(Exception e){return iso==null?"":iso.replace("T"," ").replace("Z","");}
    }
    private static String imageXml(Item it){
        int id=it.relId;
        return "<w:p><w:pPr><w:spacing w:after=\"260\"/></w:pPr><w:r><w:drawing>"+
            "<wp:inline distT=\"0\" distB=\"0\" distL=\"0\" distR=\"0\">"+
            "<wp:extent cx=\""+it.cx+"\" cy=\""+it.cy+"\"/>"+
            "<wp:docPr id=\""+id+"\" name=\"VDS foto "+id+"\" descr=\""+xml(it.description)+"\"/>"+
            "<a:graphic><a:graphicData uri=\"http://schemas.openxmlformats.org/drawingml/2006/picture\">"+
            "<pic:pic><pic:nvPicPr><pic:cNvPr id=\"0\" name=\"Foto.jpg\"/><pic:cNvPicPr/></pic:nvPicPr>"+
            "<pic:blipFill><a:blip r:embed=\"rId"+id+"\"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill>"+
            "<pic:spPr><a:xfrm><a:off x=\"0\" y=\"0\"/><a:ext cx=\""+it.cx+"\" cy=\""+it.cy+"\"/></a:xfrm>"+
            "<a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom></pic:spPr></pic:pic>"+
            "</a:graphicData></a:graphic></wp:inline></w:drawing></w:r></w:p>";
    }
    static void write(OutputStream out,String jsonString,File imageDir,String filter) throws Exception {
        JSONObject data=new JSONObject(jsonString==null||jsonString.isEmpty()?"{}":jsonString);
        JSONArray fArr=data.optJSONArray("folders"),pArr=data.optJSONArray("photos");
        if(fArr==null||pArr==null)throw new IOException("Geen VDS gegevens gevonden");
        List<Group> groups=new ArrayList<>(); Map<String,Group> byId=new HashMap<>();
        for(int i=0;i<fArr.length();i++){
            JSONObject f=fArr.optJSONObject(i);if(f==null)continue;
            Group g=new Group();g.name=f.optString("name","Locatie");groups.add(g);byId.put(f.optString("id",""),g);
        }
        int num=2; // rId1 belongs to styles.xml
        for(int i=0;i<pArr.length();i++){
            JSONObject p=pArr.optJSONObject(i);if(p==null)continue;
            String status=p.optString("status","open");
            if("trash".equals(status)||("open".equals(filter)&&!"open".equals(status))||("done".equals(filter)&&!"done".equals(status)))continue;
            Group group=byId.get(p.optString("folderId",""));if(group==null)continue;
            String id=p.optString("id","");if(!id.matches("[a-zA-Z0-9-]{1,70}"))continue;
            File source=new File(imageDir,id+".jpg");if(!source.isFile())continue;
            BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeFile(source.getAbsolutePath(),bounds);
            if(bounds.outWidth<=0||bounds.outHeight<=0)continue;
            Item item=new Item();item.id=id;item.image=source;item.description=p.optString("description","");item.takenAt=p.optString("takenAt","");item.status=status;
            double scale=Math.min(5_360_000.0/bounds.outWidth,4_200_000.0/bounds.outHeight);
            item.cx=Math.max(1,(long)(bounds.outWidth*scale));item.cy=Math.max(1,(long)(bounds.outHeight*scale));
            item.relId=num++;item.target="media/vds_"+item.relId+".jpg";group.photos.add(item);
        }
        for(Group g:groups)g.photos.sort(Comparator.comparing(it->it.takenAt));
        StringBuilder doc=new StringBuilder(16000);
        doc.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
            .append("<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\" ")
            .append("xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\" ")
            .append("xmlns:wp=\"http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing\" ")
            .append("xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\" ")
            .append("xmlns:pic=\"http://schemas.openxmlformats.org/drawingml/2006/picture\"><w:body>");
        doc.append(p("VDS punten — fotorapport","Title"));
        doc.append(p("Datum rapport: "+new java.text.SimpleDateFormat("dd-MM-yyyy HH:mm",java.util.Locale.forLanguageTag("nl-NL")).format(new java.util.Date()),""));
        String inspection=data.optString("inspectionDate","");
        if(!inspection.isEmpty())doc.append(p("Algemene keuringsdatum: "+inspection,""));
        doc.append(p("Overzicht: "+("open".equals(filter)?"Openstaande punten":"done".equals(filter)?"Afgehandelde punten":"Alle actieve foto's"),""));
        boolean first=true;int fotoCount=0;
        for(Group g:groups){if(g.photos.isEmpty())continue;
            doc.append(h(g.name,!first));first=false;
            doc.append(p("Aantal foto's: "+g.photos.size(),""));
            for(Item it:g.photos){fotoCount++;
                doc.append(p("Foto "+fotoCount+"  •  "+formatDate(it.takenAt)+"  •  "+("done".equals(it.status)?"Gedaan":"Open"),"Caption"));
                if(!it.description.trim().isEmpty())doc.append(p("Omschrijving: "+it.description,""));
                doc.append(imageXml(it));
            }
        }
        if(first)doc.append(p("Geen foto's gevonden voor deze selectie.",""));
        doc.append("<w:sectPr><w:pgSz w:w=\"11906\" w:h=\"16838\"/><w:pgMar w:top=\"1150\" w:right=\"1150\" w:bottom=\"1150\" w:left=\"1150\" w:header=\"600\" w:footer=\"600\" w:gutter=\"0\"/></w:sectPr></w:body></w:document>");
        StringBuilder rels=new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>");
        for(Group g:groups)for(Item it:g.photos)rels.append("<Relationship Id=\"rId").append(it.relId).append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/image\" Target=\"").append(it.target).append("\"/>");
        rels.append("</Relationships>");
        String types="<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"+
            "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"+
            "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"+
            "<Default Extension=\"jpg\" ContentType=\"image/jpeg\"/>"+
            "<Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/>"+
            "<Override PartName=\"/word/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml\"/>"+
            "</Types>";
        String rootRels="<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"+
            "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/></Relationships>";
        String styles="<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"+
            "<w:styles xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\">"+
            "<w:docDefaults><w:rPrDefault><w:rPr><w:rFonts w:ascii=\"Calibri\" w:hAnsi=\"Calibri\"/><w:sz w:val=\"22\"/></w:rPr></w:rPrDefault></w:docDefaults>"+
            "<w:style w:type=\"paragraph\" w:default=\"1\" w:styleId=\"Normal\"><w:name w:val=\"Normal\"/></w:style>"+
            "<w:style w:type=\"paragraph\" w:styleId=\"Title\"><w:name w:val=\"Title\"/><w:basedOn w:val=\"Normal\"/><w:rPr><w:b/><w:color w:val=\"C32937\"/><w:sz w:val=\"36\"/></w:rPr></w:style>"+
            "<w:style w:type=\"paragraph\" w:styleId=\"Heading1\"><w:name w:val=\"Heading 1\"/><w:basedOn w:val=\"Normal\"/><w:pPr><w:spacing w:before=\"300\" w:after=\"180\"/></w:pPr><w:rPr><w:b/><w:color w:val=\"181818\"/><w:sz w:val=\"34\"/></w:rPr></w:style>"+
            "<w:style w:type=\"paragraph\" w:styleId=\"Caption\"><w:name w:val=\"Caption\"/><w:basedOn w:val=\"Normal\"/><w:rPr><w:b/><w:color w:val=\"424242\"/><w:sz w:val=\"20\"/></w:rPr></w:style></w:styles>";
        try(ZipOutputStream zip=new ZipOutputStream(out)){
            entry(zip,"[Content_Types].xml",types);
            entry(zip,"_rels/.rels",rootRels);
            entry(zip,"word/document.xml",doc.toString());
            entry(zip,"word/_rels/document.xml.rels",rels.toString());
            entry(zip,"word/styles.xml",styles);
            for(Group g:groups)for(Item it:g.photos)entryFile(zip,"word/"+it.target,it.image);
        }
    }
}
