package com.ra;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.logging.Logger;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;

import org.apache.sanselan.ImageReadException;
import org.apache.sanselan.ImageWriteException;
import org.apache.sanselan.Sanselan;
import org.apache.sanselan.common.byteSources.ByteSourceFile;
import org.apache.sanselan.formats.jpeg.xmp.JpegXmpRewriter;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLFilter;
import org.xml.sax.helpers.XMLFilterImpl;

public class StandAloneXMPTrimmer {

	protected Logger logger = Logger.getLogger(getClass().getName());

	XMLFilter parser;

	private static boolean overwriteOriginals = false;
	private long runtime;

	public static void main(String[] args) throws Exception {
		if (args.length < 1) {
			usage();
		}
		File file = new File(args[0]);
		if (!file.exists()) {
			usage();
		}
		if (args.length > 1) {
			overwriteOriginals = Boolean.parseBoolean(args[1]);
		}
		StandAloneXMPTrimmer trimmer = new StandAloneXMPTrimmer();
		trimmer.processFile(file);
	}

	private static void usage() {
		System.err
				.println("USAGE: java "
						+ StandAloneXMPTrimmer.class.getName()
						+ " <file or directory to process>"
						+ " <optional: overwriteOriginalFiles (true|false defaults to false)>");
	}

	/**
	 * Default constructor. Sets up the XML Parser that will be used to
	 * guarantee XMP XML parse-ability.
	 * 
	 * @throws SAXException
	 * @throws ParserConfigurationException
	 */
	public StandAloneXMPTrimmer() throws SAXException,
			ParserConfigurationException {
		parser = new XMLFilterImpl();
		parser.setParent(SAXParserFactory.newInstance().newSAXParser()
				.getXMLReader());
		runtime = System.currentTimeMillis();
	}

	/**
	 * A recursive process method which handles processing all files contained
	 * within a given root directory.
	 * 
	 * @param file
	 *            - The file to trim or the directory containing more
	 *            directories or the files to trim.
	 * @throws Exception
	 */
	private void processFile(File file) throws Exception {

		if (file.isDirectory()) {
			// if it's a directory, loop through all children and process them.
			for (File subFile : file.listFiles()) {
				processFile(subFile);
			}
		} else {
			// Restrict the use of this trimmer to jpg files only.
			// we could do this with a Filename Filter when listing the files
			// but just
			// do it this way for simplicity
			if (file.getName().toLowerCase().endsWith(".jpg")
					|| file.getName().toLowerCase().endsWith(".jpeg")) {
				String xmpXmlAsString = null;
				try {
					try {
						xmpXmlAsString = Sanselan.getXmpXml(file);
						if (xmpXmlAsString == null) {
							logger.warning("no XMP data available for file: "
									+ file.getAbsolutePath());
						} else {
							attemptParse(file, xmpXmlAsString);
							// If the parse succeeds we have good XML and either
							// A) the image is fine or
							// B) the image has a different problem
						}
					} catch (ImageReadException e) {
						logger.warning("POSSIBLE CORRUPTION: attempted to process a non-image: "
								+ file.getAbsolutePath());
					} catch (Throwable parseException) {
						// Something is wrong with the XMP XML Block

						// We could check for the 'content in trailing section'
						// or 'content in prolog' errors specifically but the
						// only error that we've seen is the 'content in
						// trailing section' error so let's just grab everything
						// and attempt a trim.

						// create a copy of the existing file with trimmed XMP
						// XML.
						File fileWithTrimmedXmpXml = writeTrimmedXmpToFile(
								file, xmpXmlAsString);

						try {
							attemptParse(fileWithTrimmedXmpXml,
									Sanselan.getXmpXml(fileWithTrimmedXmpXml));

							String trimmedPath = fileWithTrimmedXmpXml
									.getAbsolutePath();

							// If we're overwriting the original files
							if (overwriteOriginals) {
								// NOTE: this isn't true overwriting, it still
								// always creates the copy. This is just
								// a little safer
								if (file.delete()) {
									// delete the existing one
									fileWithTrimmedXmpXml.renameTo(file);
									// rename the trimmed version to the old one
									trimmedPath = file.getAbsolutePath();
								} else {
									logger.warning("Set to overwrite existing files, but couldn't overwrite: "
											+ file.getAbsolutePath());
								}
							}

							logger.info(trimmedPath
									+ " XMP XML Has been trimmed.");
						} catch (Throwable t) {
							// If this happens, trimming did not solve the
							// issue.
							logger.severe("Parsing failed after trimming of XMP XML: "
									+ t.getMessage());
						}

					}
				} catch (Throwable t) {
					logger.severe("Unrecoverable error processing file: "
							+ file.getAbsolutePath() + " - " + t.getMessage());
				}
			} else {
				// It's a file type we can't handle...
			}
		}

	}

	private File writeTrimmedXmpToFile(File file, String xmpXmlAsString)
			throws FileNotFoundException, ImageReadException, IOException,
			ImageWriteException {
		// Trim the XMP XML
		String trimmedXmpXmlAsString = xmpXmlAsString.trim();

		// we include the runtime so that multiple runs of the trimmer do not
		// cause issues when ovewrite is set to true
		File fileWithTrimmedXmpXml = new File(file.getParent(), file.getName()
				+ ".fixed-xmp" + runtime + ".jpg");
		OutputStream os = null;
		try {
			os = new BufferedOutputStream(new FileOutputStream(
					fileWithTrimmedXmpXml));
			new JpegXmpRewriter().updateXmpXml(new ByteSourceFile(file), os,
					trimmedXmpXmlAsString);
		} finally {
			if (os != null) {
				os.close();
			}
			os = null;
		}
		return fileWithTrimmedXmpXml;
	}

	private void attemptParse(File file, String xmpXmlAsString)
			throws Throwable {
		InputStream in = null;
		try {
			in = new ByteArrayInputStream(xmpXmlAsString.getBytes());
		} catch (Throwable t) {
			logger.severe("Problem converting XMP XML into Bytes '"
					+ t.getMessage() + "' : " + file.getAbsolutePath());
		}
		try {
			parser.parse(new InputSource(in));
		} catch (Throwable t) {
			throw t;
		}
	}

}
