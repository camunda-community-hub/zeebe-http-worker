package io.zeebe.http;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletHandler;

public class HealthAndReadynessProbe {
  
  private static boolean ready = true;

  public static int PORT = 8080;
  private Server server;

  public void start() throws Exception {
    try {
      server = new Server();
      ServerConnector connector = new ServerConnector(server);
      connector.setPort(PORT);
      server.setConnectors(new Connector[] { connector });
      
      ServletHandler servletHandler = new ServletHandler();
      server.setHandler(servletHandler);
      servletHandler.addServletWithMapping(HealthyServlet.class, "/healthz");
      servletHandler.addServletWithMapping(ReadyServlet.class, "/readyz");
      
      server.start();
    }
    catch (Exception ex) {
      throw new RuntimeException("Could not start web server to serve readyness and health probes: " + ex.getMessage(), ex);
    }
  }
  
  public void stop() {
    try {
      server.stop();
    } catch (Exception e) {
      // ignore
    }
  }

  public static class HealthyServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
      response.setStatus(200); // or 503 otherwise, not yet implemented
    }
  }
  public static class ReadyServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
      if (ready) {
        response.setStatus(200);
      } else {
        response.setStatus(503);
      }
    }
  }
  

}