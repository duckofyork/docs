package ai.grakn;

import ai.grakn.exception.GraqlParsingException;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.DirectoryFileFilter;
import org.apache.commons.io.filefilter.RegexFileFilter;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collection;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.Assert.fail;

public class GraqlDocsTest {

    private static final Pattern TAG_GRAQL =
            Pattern.compile(
                    "(id=\"shell[0-9]+\">\\s*<pre>|```graql\\n)" +
                    "\\s*(.*?)\\s*" +
                    "(</pre>|```)", Pattern.DOTALL);

    private static final Pattern TEMPLATE_GRAQL =
            Pattern.compile(
                    "(id=\"shell[0-9]+\">\\s*<```graql-template\\n)" +
                            "\\s*(.*?)\\s*" +
                            "(```)", Pattern.DOTALL);

    private static final Pattern SHELL_GRAQL = Pattern.compile("^*>>>(.*?)$", Pattern.MULTILINE);

    private int numFound = 0;

    @Test
    public void testExamplesValidSyntax() throws IOException {
        File dir = new File("..");

        Collection<File> files =
                FileUtils.listFiles(dir, new RegexFileFilter(".*\\.md"), DirectoryFileFilter.DIRECTORY);

        files.forEach(this::assertFileValidSyntax);

        if (numFound < 10) {
            fail("Only found " + numFound + " Graql examples. Perhaps the regex is wrong?");
        }
    }

    private void assertFileValidSyntax(File file) {
        GraknGraph graph = DocTestUtil.getTestGraph();

        byte[] encoded = new byte[0];
        try {
            encoded = Files.readAllBytes(file.toPath());
        } catch (IOException e) {
            fail();
        }

        String contents = new String(encoded, StandardCharsets.UTF_8);

        executeAssertionOnContents(graph, TAG_GRAQL,file, contents, this::assertGraqlCodeblockValidSyntax);
        executeAssertionOnContents(graph, TEMPLATE_GRAQL, file, contents, this::assertGraqlTemplateValidSyntax);
    }

    private void executeAssertionOnContents(GraknGraph graph, Pattern pattern, File file, String contents,
                                            Fn<GraknGraph, String, String> assertion){
        Matcher matcher = pattern.matcher(contents);

        while (matcher.find()) {
            numFound += 1;

            String graqlString = matcher.group(2);

            if (!graqlString.trim().startsWith("test-ignore")) {
                assertion.apply(graph, file.toString(), graqlString);
            }
        }
    }

    private void assertGraqlCodeblockValidSyntax(GraknGraph graph, String fileName, String block) {
        Matcher shellMatcher = SHELL_GRAQL.matcher(block);

        if (shellMatcher.find()) {
            while (shellMatcher.find()) {
                String graqlString = shellMatcher.group(1);
                assertGraqlStringValidSyntax(graph, fileName, graqlString);
            }
        } else {
            assertGraqlStringValidSyntax(graph, fileName, block);
        }
    }

    private void assertGraqlStringValidSyntax(GraknGraph graph, String fileName, String graqlString) {
        try {
            parse(graph, graqlString);
        } catch (Exception e1) {
            // Try and parse line-by-line instead
            String[] lines = graqlString.split("\n");

            try {
                if (lines.length > 1) {
                    for (String line : lines) {
                        if (!line.isEmpty()) parse(graph, line);
                    }
                } else {
                    graqlFail(fileName, graqlString, e1.getMessage(), e1);
                }
            } catch (Exception e2) {
                graqlFail(fileName, graqlString, e1.getMessage() + "\nOR\n" + e2.getMessage(), e1, e2);
            }
        }
    }

    private void assertGraqlTemplateValidSyntax(GraknGraph graph, String fileName, String templateBlock){
        try {
            graph.graql().parseTemplate(templateBlock, new HashMap<>());
        } catch (GraqlParsingException e){
            graqlFail(fileName, templateBlock, e.getMessage());
        } catch (Exception e){}
    }

    private void parse(GraknGraph graph, String line) {
        // TODO: Handle this in a more elegant way
        // 'commit' is a valid command
        if (!line.trim().matches("commit;?")) {
            graph.graql().parse(line).execute();
        }
    }

    private void graqlFail(String fileName, String graqlString, String error, Exception... exceptions) {
        Stream.of(exceptions).forEach(Throwable::printStackTrace);
        fail("Failure in " + fileName + ":\n" + graqlString + "\nERROR:\n" + error);
    }

    @FunctionalInterface
    interface Fn <A, B, C> {
        void apply(A a, B b, C c);
    }
}
