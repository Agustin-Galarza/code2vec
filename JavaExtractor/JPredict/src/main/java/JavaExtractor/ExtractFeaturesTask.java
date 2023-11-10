package JavaExtractor;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.lang3.StringUtils;

import com.github.javaparser.ParseException;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;

import JavaExtractor.Common.CommandLineValues;
import JavaExtractor.Common.Common;
import JavaExtractor.FeaturesEntities.ProgramFeatures;

public class ExtractFeaturesTask implements Callable<Void> {
	CommandLineValues m_CommandLineValues;
	Path filePath;
	private BufferedWriter writer;

	public ExtractFeaturesTask(CommandLineValues commandLineValues, Path path) {
		m_CommandLineValues = commandLineValues;
		this.filePath = path;
		if (m_CommandLineValues.fnDir == null || m_CommandLineValues.fnFilename == null) {
			return;
		}
		Path fnPath = Paths.get(m_CommandLineValues.fnDir, m_CommandLineValues.fnFilename);
		File fnFile = fnPath.toFile();
		try {
			fnFile.createNewFile(); // create file if it doesn't exist
			this.writer = new BufferedWriter(new FileWriter(fnFile));
		} catch (IOException | SecurityException ex) {
			return;
		}
	}

	@Override
	public Void call() throws Exception {
		//System.err.println("Extracting file: " + filePath);
		processFile();
		//System.err.println("Done with file: " + filePath);
		return null;
	}

	public void processFile() {
		ArrayList<ProgramFeatures> features;
		CompilationUnit methodsClass = null;
		String code = null;
		try {
			code = new String(Files.readAllBytes(this.filePath));
		} catch (IOException e) {
			e.printStackTrace();
			code = Common.EmptyString;
		}
		try {
			features = extractSingleFile(code);
			methodsClass = extractSingleFileMethods(code);
		} catch (ParseException | IOException e) {
			e.printStackTrace();
			return;
		}
		if (features == null) {
			return;
		}

		String toPrint = featuresToString(features);
		String methodsToPrint = methodsToString(methodsClass);
		if (toPrint.length() > 0) {
			writeMethodsToFile(methodsToPrint);
			System.out.println(toPrint);
		}
	}

	public synchronized void writeMethodsToFile(String methodsString) {
		try {
			this.writer.write(methodsString);
		} catch (IOException ex) {
			return;
		}
	}

	public ArrayList<ProgramFeatures> extractSingleFile(String code) throws ParseException, IOException {

		FeatureExtractor featureExtractor = new FeatureExtractor(m_CommandLineValues);

		ArrayList<ProgramFeatures> features = featureExtractor.extractFeatures(code);

		return features;
	}

	public CompilationUnit extractSingleFileMethods(String code) throws ParseException, IOException {
		CompilationUnit cu = new CompilationUnit();
		ClassOrInterfaceDeclaration class_ = cu.addClass("Methods");

		FeatureExtractor featureExtractor = new FeatureExtractor(m_CommandLineValues);

		List<MethodDeclaration> methods = featureExtractor.extractASTMethods(code);
		methods.forEach(
				method -> {
					class_.addMethod(method.getName())
							.setModifiers(method.getModifiers())
							.setBody(method.getBody())
							.setParameters(method.getParameters())
							.setType(method.getType());
				});

		return cu;
	}

	public String methodsToString(CompilationUnit methodsClass) {
		final String FOUR_SPACES = "    ";
		final String EMPTY_STRING = "";
		final String DOUBLE_ENDLINE_TOKEN = "¿";

		String classString = methodsClass.toString()
				.replace("\n\n", DOUBLE_ENDLINE_TOKEN)
				.replace("\n", EMPTY_STRING)
				.replace("\t", EMPTY_STRING)
				.replace(FOUR_SPACES, EMPTY_STRING)
				.replace(DOUBLE_ENDLINE_TOKEN, "\n");

		int classOpeningIndex = classString.indexOf('{', 0);
		int classClosingIndex = classString.lastIndexOf('}');

		String methodString = classString.substring(classOpeningIndex + 2, classClosingIndex);
		if (!methodString.endsWith("\n")) {
			methodString += "\n";
		}
		return methodString;
	}

	public String featuresToString(ArrayList<ProgramFeatures> features) {
		if (features == null || features.isEmpty()) {
			return Common.EmptyString;
		}

		List<String> methodsOutputs = new ArrayList<>();

		for (ProgramFeatures singleMethodfeatures : features) {
			StringBuilder builder = new StringBuilder();
			
			String toPrint = Common.EmptyString;
			toPrint = singleMethodfeatures.toString();
			if (m_CommandLineValues.PrettyPrint) {
				toPrint = toPrint.replace(" ", "\n\t");
			}
			builder.append(toPrint);
			

			methodsOutputs.add(builder.toString());

		}
		return StringUtils.join(methodsOutputs, "\n");
	}
}
