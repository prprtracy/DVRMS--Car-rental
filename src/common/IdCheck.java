package common;

public final class IdCheck {
    private IdCheck(){}

    public static String officeLocation(String id){
        if(id == null || id.length()<3) return "Wrong customer ID searching";

        return id.substring(0,3).toUpperCase();
    }

    public static boolean isManager(String id){
        return id != null && id.length() >= 4 && Character.toUpperCase(id.charAt(3)) == 'M';
    }

    public static boolean isCustomer(String id) {
        return id != null && id.length() >= 4 && Character.toUpperCase(id.charAt(3)) == 'U';
    }

}
