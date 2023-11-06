package JavaExtractor;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import org.apache.commons.lang3.StringUtils;

import com.github.javaparser.ParseException;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;

import JavaExtractor.Common.CommandLineValues;
import JavaExtractor.Common.Common;
import JavaExtractor.FeaturesEntities.ProgramFeatures;

public class ExtractFeaturesTask implements Callable<Void> {
	CommandLineValues m_CommandLineValues;
	Path filePath;

	public ExtractFeaturesTask(CommandLineValues commandLineValues, Path path) {
		m_CommandLineValues = commandLineValues;
		this.filePath = path;
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
		try {
			features = extractSingleFile();
			methodsClass = extractSingleFileMethods();
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

	public void writeMethodsToFile(String methodsString) {
		if (m_CommandLineValues.fnDir == null || m_CommandLineValues.fnFilename == null) {
			return;
		}
		Path fnPath = Paths.get(m_CommandLineValues.fnDir, m_CommandLineValues.fnFilename);
		File fnFile = fnPath.toFile();
		try {
			fnFile.createNewFile();
			Files.write(fnPath, methodsString.getBytes());
		} catch (IOException ex) {
			return;
		}
	}

	public ArrayList<ProgramFeatures> extractSingleFile() throws ParseException, IOException {
		String code = null;
		try {
			code = new String(Files.readAllBytes(this.filePath));
		} catch (IOException e) {
			e.printStackTrace();
			code = Common.EmptyString;
		}
		FeatureExtractor featureExtractor = new FeatureExtractor(m_CommandLineValues);

		ArrayList<ProgramFeatures> features = featureExtractor.extractFeatures(code);

		return features;
	}

	public CompilationUnit extractSingleFileMethods() throws ParseException, IOException {
		CompilationUnit cu = new CompilationUnit();
		ClassOrInterfaceDeclaration class_ = cu.addClass("Methods");
		String code = null;
		try {
			code = new String(Files.readAllBytes(this.filePath));
		} catch (IOException e) {
			e.printStackTrace();
			code = Common.EmptyString;
		}
		FeatureExtractor featureExtractor = new FeatureExtractor(m_CommandLineValues);

		List<MethodDeclaration> methods = featureExtractor.extractASTMethods(code);
		methods.forEach(method -> class_.addMethod(method.getName()).setBody(method.getBody()));

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

		return classString.substring(classOpeningIndex + 2, classClosingIndex);
		// return methodsClass.toString();
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
