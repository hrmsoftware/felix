package org.apache.felix.dm.lambda.samples.future;

import static org.apache.felix.dm.lambda.DependencyManagerActivator.component;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.felix.dm.Component;
import org.osgi.service.log.LogService;

/**
 * Provides all hrefs found from a given web page.
 */
public class PageLinksImpl implements PageLinks {
	private LogService m_log;
	private final static String HREF_PATTERN = "<a\\s+href\\s*=\\s*(\"[^\"]*\"|[^\\s>]*)\\s*>";
	private List<String> m_links; // web page hrefs (links).
    private String m_url;

	PageLinksImpl(String url) {
	    m_url = url;
	}
	
	void bind(LogService log) {
		m_log = log;
	}
	
	void init(Component c) {
	    // asynchronously download the content of the URL specified in the constructor.
	    CompletableFuture<List<String>> futureLinks = CompletableFuture.supplyAsync(() -> download(m_url)) 
	        .thenApply(this::parseLinks);	       

	    // Add the future dependency so we'll be started once the CompletableFuture "futureLinks" has completed.
	    component(c, comp -> comp.withFuture(futureLinks, future -> future.cbi(this::setLinks)));
	}
	
	// Called when our future has completed.
    void setLinks(List<String> links) {
        m_links = links;
    }
    
	// once our future has completed, our component is started.
	void start() {
		m_log.log(LogService.LOG_INFO, "Service starting: number of links found from Felix web site: " + m_links.size());
	}
	
	@Override
	public List<String> getLinks() {
		return m_links;
	}

	private String download(String url) {
		try (Scanner in = new Scanner(new URL(url).openStream())) {
			StringBuilder builder = new StringBuilder();
			while (in.hasNextLine()) {
				builder.append(in.nextLine());
				builder.append("\n");
			}
			return builder.toString();
		} catch (IOException ex) {
			RuntimeException rex = new RuntimeException();
			rex.initCause(ex);
			throw rex;
		}
	}
	
	private List<String> parseLinks(String content) {		 
		Pattern pattern = Pattern.compile(HREF_PATTERN, Pattern.CASE_INSENSITIVE);
		Matcher matcher = pattern.matcher(content);
		List<String> result = new ArrayList<>();
		while (matcher.find())
			result.add(matcher.group(1));
		return result;
	}
}
