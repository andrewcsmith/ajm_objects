require 'rake/clean'
require 'tempfile'
include FileUtils

VERSION = '0.9.2'
BUILD_DATE = Time.now.utc.strftime '%B %d, %Y (%H:%M GMT)'
MANIFEST = 
"Library: ajm objects (MXJ) for MaxMSP
Version: #{VERSION}
Built-Date: #{BUILD_DATE}
Author: Adam Murray
URL: http://compusition.com
"

SRC     = 'src'
LIB     = 'lib'
PATCHES = 'ajm'
LICENSE = 'license'
BUILD   = 'build'
DIST    = 'dist'
PACKAGE = "#{DIST}/ajm-objects-#{VERSION}"

SOURCES   = FileList["#{SRC}/**/*.java"].exclude(/Test\.java$/)
CLASSPATH = FileList["#{LIB}/**/*.jar"].exclude(/^ajm.jar$/)
JAR       = "#{LIB}/ajm.jar"


##############################################################################
# TASK DEFINITIONS

CLEAN.include BUILD, JAR
CLOBBER.include DIST


desc 'compile the java source files'
task :compile do
  mkdir BUILD
  puts "Compiling java sources to #{BUILD}/"  
  `javac -classpath #{CLASSPATH.join ':'} -d #{BUILD} -g -source 1.5 -target 1.5 #{SOURCES}`
end


desc 'construct the jar archive of the compiled java sources'
task :jar => [:clean, :compile] do
  manifest = Tempfile.new('manifest')
  File.open(manifest, 'w') {|io| io.write MANIFEST }
  puts "Constructing #{JAR}"
  `jar cvfm #{JAR} #{manifest.path} -C #{BUILD} .`  
end


desc 'prepare the files for distribution'
task :package => [:jar] do
  puts "Preparing distribution package"
  package_lib = "#{PACKAGE}/#{LIB}"  
  mkdir DIST
  mkdir PACKAGE
  mkdir package_lib
  
  # Collect the files
  FileList['*.txt', '*.example'].each do |filename|
    cp filename, PACKAGE
  end
  FileList["#{LIB}/*.jar"].exclude('max.jar', /^.*junit.*jar$/, 'bsf.jar').each do |filename|
    cp filename, package_lib
  end  
  cp_r PATCHES, PACKAGE
  cp_r LICENSE, PACKAGE
end


desc 'search and replace variable values in text files'
task :replace_vars => [:package] do
  puts "Performing search and replace for the VERSION and BUILD_DATE variables"
  plaintext_filetypes = ['txt', 'maxpat', 'maxhelp']
  files = FileList[ plaintext_filetypes.map{|type| "#{PACKAGE}/**/*.#{type}" } ]
  files.replace_all '@@VERSION', VERSION
  files.replace_all '@@BUILD_DATE', BUILD_DATE
end


desc 'construct the distribution archive'
task :dist => [:replace_vars] do
  mkdir DIST
  archive = "#{DIST}/#{PACKAGE}.zip"
  puts "Constructing distribution archive #{archive}"
  `zip -l -r #{archive} #{PACKAGE}`
  # The -l option converts newlines to crlf, which should display correctly on both OS X and Windows.
  # Otherwise, since I write these txt files on OS X, newlines would disappear when viewed in Notepad on Windows.
end 


##############################################################################
# SUPPORT CODE:

# I find it annoying that I always have to check if a directory exists
# before creating it, so I monkey patch mkdir() to handle it automatically:
alias original_mkdir mkdir 
def mkdir(dir)
  original_mkdir dir if not File.exist? dir
end


class FileList
  # Replaces all occurrences of token with value
  def replace_all(token, value)
    self.each do |filename|
      next if File.directory? filename
      contents = IO.read filename
      if contents.include? token
        contents.gsub! token, value
        File.open(filename, 'w') do |io|
          io.write contents
        end
      end    
    end
  end
end
