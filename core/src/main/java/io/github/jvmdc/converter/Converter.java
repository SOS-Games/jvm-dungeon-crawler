package io.github.jvmdc.converter;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.Writer;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.Properties;
import java.util.ArrayList;
import java.util.List;
import java.io.IOException;
import java.io.InputStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonString;
import com.badlogic.gdx.utils.JsonWriter.OutputType;
import java.util.stream.Collectors;

// must first run:
// gradlew.bat :core:generateGrammarSource
// then you can run this:
// gradlew.bat :core:converter --args="help"
public class Converter {

    public static void main(String[] args) {
        Properties p = getLocalPropeties();
        getRonFiles(p);
        //printVoxData(p);
    }

    // velorenAssetPath should start with /assets
    public static void convertAsset(Path ronAbsolutePath, Path velorenAssetPath) {
        if (ronAbsolutePath.getFileName().toString().equals("credits.ron")) {
            return;
        }

        try {
            Path relativeAssetPath = velorenAssetPath.relativize(ronAbsolutePath); // get paths starting one layer at /assets

            RonLexer lexer = new RonLexer(CharStreams.fromPath(ronAbsolutePath));
            CommonTokenStream tokens = new CommonTokenStream(lexer);
            RonParser parser = new RonParser(tokens);
            org.antlr.v4.runtime.RuleContext tree = parser.ron_();
    
            RonToJsonVisitor visitor = new RonToJsonVisitor();
            String json = visitor.visit(tree);
    
            JsonString j = new JsonString();
            j.json(json);
            Json js = new Json();
            js.setOutputType(OutputType.json);
            String formattedContents = js.prettyPrint(j.toString());

            // create directory structure and asset file inside of it
            Path directoryStructure = relativeAssetPath.getParent();
            Files.createDirectories(directoryStructure);

            // convert extension from .ron to .json
            String jsonFileName = relativeAssetPath.getFileName().toString().split("[.]")[0] + ".json";
            Path jsonPath = Path.of(jsonFileName);

            // append jsonPath to directoryStructure
            Path jsonAssetPath = directoryStructure.resolve(jsonPath);

            if (!Files.exists(jsonAssetPath)) {
                Files.createFile(jsonAssetPath);
            }
            
            OutputStream outputStream = Files.newOutputStream(jsonAssetPath);
            Writer out = new BufferedWriter(new OutputStreamWriter(outputStream));
            out.write(formattedContents);
            out.flush();
            out.close();
            System.out.println("wrote to " + jsonAssetPath);
        } catch (IOException e) {
            System.out.println("IOException for ron file " + ronAbsolutePath.toString());
            // print stack trace
            e.printStackTrace();
            return;
        }

    }

    public static void printVoxData(Properties p) {
        String velorenPathString = p.getProperty("velorenPath");
        if (velorenPathString.isEmpty()) {
            System.out.println("no velorenPath");
            return;
        }
        File voxFile = new File(velorenPathString + "/assets/voxygen/voxel/weapon/sword/starter.vox");
        if (!voxFile.exists()) {
            System.out.println("vox file does not exist: " + voxFile.toString());
            return;
        }
        try {
            VoxReader.VoxData voxData = VoxReader.read(voxFile);

            System.out.println("Dimensions: " + voxData.sizeX + "x" + voxData.sizeY + "x" + voxData.sizeZ);
            // Count how many non-empty voxels exist
            int solidCount = 0;
            for (int x = 0; x < voxData.sizeX; x++) {
                for (int y = 0; y < voxData.sizeY; y++) {
                    for (int z = 0; z < voxData.sizeZ; z++) {
                        if (voxData.grid[x][y][z] != 0) solidCount++;
                    }
                }
            }
            System.out.println("Solid voxels: " + solidCount);
            System.out.println("Palette Color #1: 0x" + Integer.toHexString(voxData.palette[0]));
        } catch (IOException e) {
            System.out.println("IOException for vox file " + voxFile.toString());
            e.printStackTrace();
        }
    }

    public static void getRonFiles(Properties p) {
        String velorenPathString = p.getProperty("velorenPath");
        if (velorenPathString.isEmpty()) {
            System.out.println("no velorenPath");
            return;
        }
        Path velorenAssetPath = Path.of(velorenPathString + "/assets");


        List<Path> ronAbsolutePaths = new ArrayList<Path>();

        try {
            Files.walk(velorenAssetPath)
                .filter(path -> path.toString().endsWith(".ron"))
                .forEach(path -> ronAbsolutePaths.add(path));

            // also note these existing file types:
            //[ron, canary, vox, ogg, jpg, png, ttf, txt, ftl, md, obj, ico, desktop, xml, glsl, frag, vert, bin]
            /*
            List<String> uniqueExtensions = Files.walk(velorenAssetPath)
                .map(path -> path.getFileName().toString().split("[.]"))
                .filter(parts -> parts.length > 1)
                .map(parts -> parts[parts.length - 1])
                .distinct()
                .collect(Collectors.toList());
            System.out.println(uniqueExtensions);
            */
        } catch (IOException e) {
        }

        ronAbsolutePaths.stream().forEach(path -> convertAsset(path, Path.of(velorenPathString)));
    }


    public static Properties getLocalPropeties() {
        // make sure to use forward slashes "/" in property paths
        Path local = Path.of("local.properties");

        Properties p = new Properties();

        if (!Files.isRegularFile(local)) {
            return p;
        }

        try {
            InputStream in = Files.newInputStream(local);
            p.load(in);
        } catch (IOException e) {
            return p;
        }

        return p;
    }
}
