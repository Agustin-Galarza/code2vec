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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
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

	private static ConcurrentMap<File, BufferedWriter> writers = new ConcurrentHashMap<>();
	private static ConcurrentMap<File, Integer> activeWriters = new ConcurrentHashMap<>();
	private static BufferedWriter logWriter;

	static {
		Path logPath = Paths.get("./logs");
		File f = logPath.toFile();
		try {
			f.createNewFile();
			logWriter = new BufferedWriter(new FileWriter(f));
		} catch (IOException ex) {
			System.out.println("ERROR: could not create log file");
			ex.printStackTrace();
		}
	}

	private static void logMessage(String msg) {
		try {
			logWriter.write(msg);
		} catch (Exception e) {
			System.out.println("ERROR: could not write to log file");
			e.printStackTrace();
		}
	}

	private BufferedWriter getWriter(File file) {
		Integer writersCount = activeWriters.get(file);
		if (writersCount == null) {
			activeWriters.put(file, 0);
			try {
				file.createNewFile();
				writers.put(file, new BufferedWriter(new FileWriter(file)));
			} catch (IOException | SecurityException ex) {
				logMessage("Error could not create writer for file " + file);
				ex.printStackTrace();
				return null;
			}
		}
		activeWriters.compute(file, (k, v) -> v + 1);
		return writers.get(file);
	}

	private void removeWriter(File file) {
		Integer writersCount = activeWriters.compute(file, (k, v) -> v - 1);
		if (writersCount == null) {
			return;
		}
		if (writersCount == 0) {
			activeWriters.remove(file);
			BufferedWriter wr = writers.remove(file);
			try {
				wr.close();
			} catch (IOException ex) {
				logMessage("Error: could not close file " + file);
				ex.printStackTrace();
				return;
			}
		}
	}

	public ExtractFeaturesTask(CommandLineValues commandLineValues, Path path) {
		m_CommandLineValues = commandLineValues;
		this.filePath = path;
		if (m_CommandLineValues.fnDir == null || m_CommandLineValues.fnFilename == null) {
			return;
		}
		Path fnPath = Paths.get(m_CommandLineValues.fnDir, m_CommandLineValues.fnFilename);
		File fnFile = fnPath.toFile();
		this.writer = getWriter(fnFile);
	}

	public void onExit() {
		removeWriter(filePath.toFile());
	}

	@Override
	public Void call() throws Exception {
		//System.err.println("Extracting file: " + filePath);
		processFile();
		onExit();
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
			logMessage("Error could not write method");
			ex.printStackTrace();
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
