package com.tw.pkg.deb.repo;

import com.tw.pkg.deb.db.PackageDAO;
import com.tw.pkg.deb.helper.IOHelper;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.nio.channels.FileLock;
import java.util.Collections;
import java.util.List;

public class DebianRepoQuery {
    private String rootDirectory;
    private String lockFilePath;
    private DebianRepository debianRepository;
    private PackageDAO packageDAO;

    public DebianRepoQuery(String packagesZipURL) throws Exception {
        this.rootDirectory = System.getProperty("java.io.tmpdir") + "deb-repo-query" + File.separator + packagesZipURL.hashCode();
        System.out.println("root dir: " + rootDirectory);
        lockFilePath = rootDirectory + File.separator + "filelock";
        String databaseFilePath = rootDirectory + File.separator + "cache.db";

        new File(this.rootDirectory).mkdirs();
        File lockFile = new File(lockFilePath);
        if (!lockFile.exists()) {
            FileUtils.writeStringToFile(lockFile, "locked");
        }

        this.debianRepository = new DebianRepository(packagesZipURL, rootDirectory);
        this.packageDAO = new PackageDAO(databaseFilePath);
    }

    PackageDAO getPackageDAO() {
        return packageDAO;
    }

    public void updateCacheIfRequired() throws Exception {
        if (!debianRepository.isRepositoryValid()) {
            System.out.println("invalid repository!");
            System.exit(0);
        }

        FileLock lock = null;
        try {
            lock = IOHelper.getLockOnFile(lockFilePath);

            if (debianRepository.hasChanges()) {
                System.out.println("repository has changes. updating cache...");

                updateCache();
            } else {
                System.out.println("repository has not changed since last run...");
            }
        } finally {
            IOHelper.releaseFileLock(lock);
        }
    }

    private void updateCache() throws Exception {
        List<DebianPackage> allPackages = debianRepository.getAllPackages();
        int packageCount = allPackages.size();
        System.out.println("repository packages count: " + packageCount);

        packageDAO.createTableIfNotExists();
        if (packageDAO.getPackageCount() > 0) {
            System.out.println("cache found. updating cache with new data...");
            packageDAO.updateIfRequired(allPackages);
        } else {
            System.out.println("no cache found. populating all data...");
            for (int i = 0; i < allPackages.size(); i++) {
                DebianPackage currentPackage = allPackages.get(i);
                packageDAO.insert(currentPackage);

                if (i % 1000 == 0) {
                    System.out.println("processed: " + i + " of " + packageCount);
                }
            }
            System.out.println("finished processing all packages...");
        }
    }

    public List<DebianPackage> getDebianPackagesFor(String packageName, String versionSpec, String architecture) throws Exception {
        List<DebianPackage> packages = null;
        FileLock lock = null;
        try {
            lock = IOHelper.getLockOnFile(lockFilePath);

            packages = packageDAO.getPackagesBy_Name_Version_Architecture(packageName, versionSpec, architecture);
        } finally {
            IOHelper.releaseFileLock(lock);
        }

        if (packages != null && packages.size() > 1) {
            sortPackagesOnVersion(packages);
        }
        return packages;
    }

    private void sortPackagesOnVersion(List<DebianPackage> packages) {
        for (DebianPackage current : packages) {
            current.parseVersion();
        }
        Collections.sort(packages);
        Collections.reverse(packages);
    }
}
