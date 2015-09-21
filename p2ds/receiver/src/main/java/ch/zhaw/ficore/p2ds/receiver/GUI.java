package ch.zhaw.ficore.p2ds.receiver;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.slf4j.LoggerFactory;
import org.slf4j.ext.XLogger;

import ch.zhaw.ficore.p2ds.group.json.DataSets;

import com.hp.gagawa.java.elements.Body;
import com.hp.gagawa.java.elements.Div;
import com.hp.gagawa.java.elements.H1;
import com.hp.gagawa.java.elements.Head;
import com.hp.gagawa.java.elements.Html;
import com.hp.gagawa.java.elements.Link;
import com.hp.gagawa.java.elements.Table;
import com.hp.gagawa.java.elements.Td;
import com.hp.gagawa.java.elements.Text;
import com.hp.gagawa.java.elements.Title;
import com.hp.gagawa.java.elements.Tr;

@Path("/receiver")
public class GUI {

    private static String cssURL = "/res/style.css";

    private static final XLogger LOGGER = new XLogger(
            LoggerFactory.getLogger(GUI.class));

    private final static List<Result> results = new ArrayList<Result>();

    public static Body getBody(final Div mainDiv) {
        mainDiv.setCSSClass("mainDiv");
        Div containerDiv = new Div().setCSSClass("containerDiv");
        mainDiv.appendChild(new H1().appendChild(new Text(
                "FinalResultsReceiver")));
        Div navDiv = new Div().setCSSClass("navDiv");
        containerDiv.appendChild(navDiv);
        containerDiv.appendChild(mainDiv);

        navDiv.appendChild(new Div().setStyle("clear: both"));
        return new Body().appendChild(containerDiv);
    }

    public static Html getHtml(final String title, final HttpServletRequest req) {
        Html html = new Html();
        Head head = new Head().appendChild(new Title().appendChild(new Text(
                title)));
        html.appendChild(head);
        head.appendChild(new Link().setHref(req.getContextPath() + cssURL)
                .setRel("stylesheet").setType("text/css"));

        return html;
    }

    @Context
    HttpServletRequest request;

    @POST
    @Path("/receive")
    @Consumes({ MediaType.APPLICATION_JSON })
    public Response receive(final DataSets ds) {
        synchronized (GUI.results) {
            for (String s : ds.getData()) {
                Result r = new Result();
                r.data = s;
                r.from = this.request.getRemoteAddr();
                r.when = new Date().toGMTString();
                GUI.results.add(r);
                LOGGER.info("data set received: " + r.data);
            }
        }
        return Response.ok("OK").build();
    }

    @GET()
    @Path("/show")
    public Response showResults() {
        Html html = getHtml("Results", this.request);
        Div mainDiv = new Div();
        html.appendChild(getBody(mainDiv));

        Table tbl = new Table();
        Tr tr = new Tr();
        tr.appendChild(new Td().appendChild(new Text("FROM")));
        tr.appendChild(new Td().appendChild(new Text("WHEN")));
        tr.appendChild(new Td().appendChild(new Text("DATA")));
        tbl.appendChild(tr);

        for (Result r : GUI.results) {
            tr = new Tr();
            tr.appendChild(new Td().appendChild(new Text(r.from)));
            tr.appendChild(new Td().appendChild(new Text(r.when)));
            tr.appendChild(new Td().appendChild(new Text(r.data)));
            tbl.appendChild(tr);
        }

        mainDiv.appendChild(tbl);

        return Response.ok(html.write()).type(MediaType.TEXT_HTML).build();
    }
}
